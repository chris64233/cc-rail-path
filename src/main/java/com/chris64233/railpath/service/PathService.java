package com.chris64233.railpath.service;

import com.chris64233.railpath.api.EventView;
import com.chris64233.railpath.api.LegRequest;
import com.chris64233.railpath.api.OccupancyView;
import com.chris64233.railpath.api.PathRequest;
import com.chris64233.railpath.api.ReservationView;
import com.chris64233.railpath.domain.EventType;
import com.chris64233.railpath.domain.PathEvent;
import com.chris64233.railpath.domain.PathLeg;
import com.chris64233.railpath.domain.PathReservation;
import com.chris64233.railpath.error.ApiException;
import com.chris64233.railpath.error.ErrorType;
import com.chris64233.railpath.repository.PathEventRepository;
import com.chris64233.railpath.repository.PathReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 径路申请的批准、整体改线、取消与查询。
 *
 * <p>所有资源占用的校验与写入都在同一个数据库事务内完成：任一节点冲突则整体回滚，
 * 绝不会留下部分径路。资源行级悲观锁保证并发申请在相同区间/车站上串行判定。
 */
@Service
public class PathService {

    private final PathReservationRepository reservationRepository;
    private final PathEventRepository eventRepository;
    private final FeasibilityGuard feasibilityGuard;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PathService(PathReservationRepository reservationRepository,
                       PathEventRepository eventRepository,
                       FeasibilityGuard feasibilityGuard,
                       TransactionTemplate transactionTemplate,
                       Clock clock) {
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
        this.feasibilityGuard = feasibilityGuard;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * 批准径路申请。
     *
     * <p>以外部运行号幂等：相同编号、相同内容的重放直接返回原结果；
     * 相同编号但内容不同返回冲突。
     */
    public ReservationView approve(PathRequest request) {
        List<Plans.Occupancy> occupancy = Plans.expand(request);
        String hash = Plans.hash(request);

        try {
            return transactionTemplate.execute(status -> approveInTransaction(request, occupancy, hash));
        } catch (DataIntegrityViolationException duplicate) {
            // 并发提交相同外部运行号：唯一约束兜底。原事务已回滚，在新事务中读取既有结果，
            // 新事务同时保证径路段（懒加载）可被读取映射。
            return transactionTemplate.execute(status -> {
                PathReservation existing = reservationRepository.findByExternalRunNo(request.externalRunNo())
                        .orElseThrow(() -> duplicate);
                if (existing.getRequestHash().equals(hash)) {
                    return toView(existing);
                }
                throw new ApiException(ErrorType.CONFLICT, "CONFLICT_RUN_NO_CONTENT_MISMATCH",
                        "外部运行号 " + request.externalRunNo() + " 已存在，但申请内容与原申请不同");
            });
        }
    }

    private ReservationView approveInTransaction(PathRequest request,
                                                 List<Plans.Occupancy> occupancy,
                                                 String hash) {
        // 加资源锁后做权威的幂等判定与容量校验：并发提交相同运行号时，
        // 后到者在锁上排队、待前者提交后读到既有径路并回放，而不是误报容量冲突。
        FeasibilityGuard.ApprovalResult result =
                feasibilityGuard.verifyForApproval(occupancy, request.externalRunNo(), hash);
        if (result.verdict() == FeasibilityGuard.ApprovalVerdict.REPLAY) {
            return toView(result.existing());
        }

        Instant now = Instant.now(clock);
        PathReservation reservation = new PathReservation(request.externalRunNo(), hash, now);
        occupancy.forEach(o -> reservation.addLeg(o.seq(), o.type(), o.code(), o.entry(), o.exit()));
        reservationRepository.save(reservation);
        eventRepository.save(new PathEvent(request.externalRunNo(), EventType.APPROVED, now,
                snapshot("APPROVED", occupancy, null)));
        return toView(reservation);
    }

    /**
     * 整体改线。仅允许尚未发车（当前时间早于首段进入时间）的径路改线。
     * 新径路必须完整取得全部占用后，旧径路才释放；任一新节点冲突则原计划保持不变。
     */
    public ReservationView reroute(String externalRunNo, List<LegRequest> newLegs) {
        PathRequest normalized = new PathRequest(externalRunNo, newLegs);
        List<Plans.Occupancy> newOccupancy = Plans.expand(normalized);
        String newHash = Plans.hash(newLegs);

        return transactionTemplate.execute(status -> {
            PathReservation reservation = requireReservation(externalRunNo);
            if (reservation.isCancelled()) {
                throw new ApiException(ErrorType.CONFLICT, "CONFLICT_ALREADY_CANCELLED",
                        "径路已取消，不能改线");
            }

            Instant firstEntry = reservation.getLegs().get(0).getEntryTime();
            if (!Instant.now(clock).isBefore(firstEntry)) {
                throw new ApiException(ErrorType.CONFLICT, "CONFLICT_ALREADY_DEPARTED",
                        "列车已于 " + firstEntry + " 发车，不能整体改线");
            }

            if (reservation.getRequestHash().equals(newHash)) {
                return toView(reservation);
            }

            List<Plans.Occupancy> oldOccupancy = toOccupancy(reservation);

            // 校验新径路时排除自身旧占用；旧占用在此方法成功返回后的提交点才随替换释放。
            feasibilityGuard.verifyForReroute(newOccupancy, reservation.getId());

            List<PathLeg> built = newOccupancy.stream()
                    .map(o -> new PathLeg(null, o.seq(), o.type(), o.code(), o.entry(), o.exit()))
                    .toList();
            // 先清空旧占用并立即 flush 删除，释放 (径路,序号) 唯一键后再写入新段。
            reservation.clearLegs();
            reservationRepository.saveAndFlush(reservation);
            // 新径路占用已全部校验通过，此刻才在同一事务内写入新占用与哈希。
            reservation.applyReroute(newHash, built);

            Instant now = Instant.now(clock);
            eventRepository.save(new PathEvent(externalRunNo, EventType.REROUTED, now,
                    snapshot("REROUTED", newOccupancy, oldOccupancy)));
            return toView(reservation);
        });
    }

    /**
     * 取消径路。占用记录保留以备查询完整占用明细，但状态置为 CANCELLED 后
     * 不再参与任何容量计算。取消操作幂等。
     */
    public ReservationView cancel(String externalRunNo) {
        return transactionTemplate.execute(status -> {
            PathReservation reservation = requireReservation(externalRunNo);
            if (!reservation.isCancelled()) {
                reservation.cancel();
                eventRepository.save(new PathEvent(externalRunNo, EventType.CANCELLED,
                        Instant.now(clock), null));
            }
            return toView(reservation);
        });
    }

    @Transactional(readOnly = true)
    public ReservationView get(String externalRunNo) {
        return toView(requireReservation(externalRunNo));
    }

    @Transactional(readOnly = true)
    public List<EventView> events(String externalRunNo) {
        requireReservation(externalRunNo);
        return eventRepository.findByExternalRunNoOrderByIdAsc(externalRunNo).stream()
                .map(PathService::toEventView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventView> allEvents() {
        return eventRepository.findAllByOrderByIdAsc().stream()
                .map(PathService::toEventView)
                .toList();
    }

    private PathReservation requireReservation(String externalRunNo) {
        return reservationRepository.findByExternalRunNo(externalRunNo)
                .orElseThrow(() -> new ApiException(ErrorType.RESOURCE_NOT_FOUND,
                        "RESOURCE_NOT_FOUND", "径路不存在：外部运行号 " + externalRunNo));
    }

    private List<Plans.Occupancy> toOccupancy(PathReservation reservation) {
        return reservation.getLegs().stream()
                .map(l -> new Plans.Occupancy(l.getSeq(), l.getLegType(), l.getResourceCode(),
                        l.getEntryTime(), l.getExitTime()))
                .toList();
    }

    /**
     * 构造事件快照 JSON。内容受控（代码、枚举、时间戳均无任意特殊字符），
     * 这里手写最小 JSON 以避免引入额外序列化依赖。
     */
    private String snapshot(String action, List<Plans.Occupancy> after, List<Plans.Occupancy> before) {
        StringBuilder sb = new StringBuilder("{\"action\":\"").append(action).append("\",\"after\":");
        appendOccupancies(sb, after);
        if (before != null) {
            sb.append(",\"before\":");
            appendOccupancies(sb, before);
        }
        return sb.append('}').toString();
    }

    private void appendOccupancies(StringBuilder sb, List<Plans.Occupancy> occupancies) {
        sb.append('[');
        for (int i = 0; i < occupancies.size(); i++) {
            Plans.Occupancy o = occupancies.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"seq\":").append(o.seq())
                    .append(",\"type\":\"").append(o.type().name())
                    .append("\",\"resource\":\"").append(escape(o.code()))
                    .append("\",\"entry\":\"").append(o.entry())
                    .append("\",\"exit\":\"").append(o.exit()).append("\"}");
        }
        sb.append(']');
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static ReservationView toView(PathReservation reservation) {
        List<OccupancyView> legs = reservation.getLegs().stream()
                .sorted(ComparatorHolder.INSTANCE)
                .map(l -> new OccupancyView(l.getSeq(), l.getLegType().name(), l.getResourceCode(),
                        l.getEntryTime(), l.getExitTime()))
                .toList();
        return new ReservationView(reservation.getExternalRunNo(), reservation.getStatus().name(),
                reservation.getRequestHash(), reservation.getCreatedAt(), legs);
    }

    private static EventView toEventView(PathEvent event) {
        return new EventView(event.getId(), event.getExternalRunNo(), event.getEventType().name(),
                event.getOccurredAt(), event.getDetail());
    }

    /** 延迟持有比较器，避免实体 API 暴露顺序细节。 */
    private static final class ComparatorHolder {
        static final java.util.Comparator<PathLeg> INSTANCE =
                java.util.Comparator.comparingInt(PathLeg::getSeq);
    }
}
