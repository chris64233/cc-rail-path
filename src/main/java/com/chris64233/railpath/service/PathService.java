package com.chris64233.railpath.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.railpath.domain.PathEvent;
import com.chris64233.railpath.domain.PathEventType;
import com.chris64233.railpath.domain.PathLeg;
import com.chris64233.railpath.domain.PathStatus;
import com.chris64233.railpath.domain.PathStop;
import com.chris64233.railpath.domain.Section;
import com.chris64233.railpath.domain.Station;
import com.chris64233.railpath.domain.TrainPath;
import com.chris64233.railpath.repository.PathEventRepository;
import com.chris64233.railpath.repository.PathLegRepository;
import com.chris64233.railpath.repository.PathStopRepository;
import com.chris64233.railpath.repository.SectionRepository;
import com.chris64233.railpath.repository.StationRepository;
import com.chris64233.railpath.repository.TrainPathRepository;
import com.chris64233.railpath.web.dto.EventView;
import com.chris64233.railpath.web.dto.LegRequest;
import com.chris64233.railpath.web.dto.LegView;
import com.chris64233.railpath.web.dto.PathApplyRequest;
import com.chris64233.railpath.web.dto.PathResponse;
import com.chris64233.railpath.web.dto.RerouteRequest;
import com.chris64233.railpath.web.dto.StopRequest;
import com.chris64233.railpath.web.dto.StopView;

/**
 * 径路审批核心服务。
 *
 * <p>所有写操作在单个事务内完成：先按编号升序对涉及的区间/车站加悲观写锁，
 * 再校验时间连续性、区间追踪间隔与通过能力、车站到发线容量，
 * 全部通过后一次性写入全部占用；任一节点冲突即整体回滚，不留部分径路。
 */
@Service
public class PathService {

    private final TrainPathRepository paths;
    private final SectionRepository sections;
    private final StationRepository stations;
    private final PathLegRepository legs;
    private final PathStopRepository stops;
    private final PathEventRepository events;
    private final Clock clock;

    public PathService(TrainPathRepository paths, SectionRepository sections, StationRepository stations,
            PathLegRepository legs, PathStopRepository stops, PathEventRepository events, Clock clock) {
        this.paths = paths;
        this.sections = sections;
        this.stations = stations;
        this.legs = legs;
        this.stops = stops;
        this.events = events;
        this.clock = clock;
    }

    /** 申请结果：created=false 表示相同编号相同内容的幂等重放。 */
    public record ApplyOutcome(PathResponse path, boolean created) {
    }

    /**
     * 申请新径路。按外部运行号幂等：相同编号且内容相同的重放返回原结果，
     * 内容不同返回冲突。
     */
    @Transactional
    public ApplyOutcome apply(PathApplyRequest request) {
        validateTimeline(request.legs(), request.stopsOrEmpty());
        String contentHash = contentHash(request.legs(), request.stopsOrEmpty());

        var existing = paths.findByExternalRef(request.externalRef());
        if (existing.isPresent()) {
            TrainPath path = existing.get();
            if (path.getContentHash().equals(contentHash)) {
                return new ApplyOutcome(toResponse(path), false);
            }
            throw new ApiException(ErrorCode.CONFLICT,
                    "运行号 " + request.externalRef() + " 已存在，但申请内容不同");
        }

        Map<String, Section> sectionMap = lockSections(request.legs());
        Map<String, Station> stationMap = lockStations(request.stopsOrEmpty());
        checkSectionConstraints(request.legs(), sectionMap, null);
        checkStationConstraints(request.stopsOrEmpty(), stationMap, null);

        TrainPath path = new TrainPath(request.externalRef(), contentHash, now());
        occupy(path, request.legs(), request.stopsOrEmpty(), sectionMap, stationMap);
        path.addEvent(new PathEvent(path, PathEventType.APPROVED, now(), snapshot(path)));
        paths.save(path);
        return new ApplyOutcome(toResponse(path), true);
    }

    /**
     * 整体改线：仅未发车径路允许。新径路完整取得（校验+占用写入）后才释放旧径路；
     * 任一步失败事务回滚，原计划不变。
     */
    @Transactional
    public PathResponse reroute(String externalRef, RerouteRequest request) {
        validateTimeline(request.legs(), request.stopsOrEmpty());
        TrainPath path = paths.findByExternalRefForUpdate(externalRef)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "运行号不存在: " + externalRef));
        if (path.getStatus() != PathStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE, "径路已取消，不能改线");
        }
        LocalDateTime firstEnter = path.getLegs().getFirst().getEnterTime();
        if (!now().isBefore(firstEnter)) {
            throw new ApiException(ErrorCode.INVALID_STATE, "列车已发车，不能改线");
        }

        Map<String, Section> sectionMap = lockSections(request.legs());
        Map<String, Station> stationMap = lockStations(request.stopsOrEmpty());
        // 冲突校验排除本径路自身占用
        checkSectionConstraints(request.legs(), sectionMap, path.getId());
        checkStationConstraints(request.stopsOrEmpty(), stationMap, path.getId());

        path.clearOccupancy();
        occupy(path, request.legs(), request.stopsOrEmpty(), sectionMap, stationMap);
        path.setContentHash(contentHash(request.legs(), request.stopsOrEmpty()));
        path.addEvent(new PathEvent(path, PathEventType.REROUTED, now(), snapshot(path)));
        return toResponse(path);
    }

    /** 取消径路：释放全部占用，保留不可变事件。 */
    @Transactional
    public void cancel(String externalRef) {
        TrainPath path = paths.findByExternalRefForUpdate(externalRef)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "运行号不存在: " + externalRef));
        if (path.getStatus() != PathStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE, "径路已取消，不能重复取消");
        }
        String snapshot = snapshot(path);
        path.cancel();
        path.addEvent(new PathEvent(path, PathEventType.CANCELLED, now(), snapshot));
    }

    /** 查询径路完整占用明细。 */
    @Transactional(readOnly = true)
    public PathResponse getPath(String externalRef) {
        return paths.findByExternalRef(externalRef)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "运行号不存在: " + externalRef));
    }

    /** 查询径路的不可变事件流。 */
    @Transactional(readOnly = true)
    public List<EventView> getEvents(String externalRef) {
        TrainPath path = paths.findByExternalRef(externalRef)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "运行号不存在: " + externalRef));
        return events.findByPathIdOrderByIdAsc(path.getId()).stream()
                .map(e -> new EventView(e.getType().name(), e.getOccurredAt(), e.getDetail()))
                .toList();
    }

    // ---- 内部逻辑 ----

    private void occupy(TrainPath path, List<LegRequest> newLegs, List<StopRequest> newStops,
            Map<String, Section> sectionMap, Map<String, Station> stationMap) {
        for (int i = 0; i < newLegs.size(); i++) {
            LegRequest leg = newLegs.get(i);
            path.addLeg(new PathLeg(path, sectionMap.get(leg.sectionCode()), i, leg.enterTime(), leg.exitTime()));
        }
        for (int i = 0; i < newStops.size(); i++) {
            StopRequest stop = newStops.get(i);
            path.addStop(new PathStop(path, stationMap.get(stop.stationCode()), i, stop.arriveTime(), stop.departTime()));
        }
    }

    /** 时间线校验：区间按时间顺序连续不重叠，停靠位于径路时间窗内且互不重叠。 */
    private void validateTimeline(List<LegRequest> newLegs, List<StopRequest> newStops) {
        for (LegRequest leg : newLegs) {
            if (!leg.enterTime().isBefore(leg.exitTime())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "区间 " + leg.sectionCode() + " 进入时间必须早于离开时间");
            }
        }
        for (int i = 1; i < newLegs.size(); i++) {
            if (newLegs.get(i).enterTime().isBefore(newLegs.get(i - 1).exitTime())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "径路区间必须按时间顺序连续且不重叠: " + newLegs.get(i).sectionCode());
            }
        }
        LocalDateTime windowStart = newLegs.getFirst().enterTime();
        LocalDateTime windowEnd = newLegs.getLast().exitTime();
        for (StopRequest stop : newStops) {
            if (!stop.arriveTime().isBefore(stop.departTime())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "车站 " + stop.stationCode() + " 到达时间必须早于出发时间");
            }
            if (stop.arriveTime().isBefore(windowStart) || stop.departTime().isAfter(windowEnd)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "车站 " + stop.stationCode() + " 停靠时间必须位于径路时间范围内");
            }
        }
        for (int i = 1; i < newStops.size(); i++) {
            if (newStops.get(i).arriveTime().isBefore(newStops.get(i - 1).departTime())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "车站停靠必须按时间顺序排列且不重叠: " + newStops.get(i).stationCode());
            }
        }
    }

    /** 按编号升序逐个加锁，避免并发申请相互死锁。 */
    private Map<String, Section> lockSections(List<LegRequest> newLegs) {
        Map<String, Section> result = new LinkedHashMap<>();
        List<String> codes = newLegs.stream().map(LegRequest::sectionCode).distinct().sorted().toList();
        for (String code : codes) {
            result.put(code, sections.findByCodeForUpdate(code)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "区间不存在: " + code)));
        }
        return result;
    }

    private Map<String, Station> lockStations(List<StopRequest> newStops) {
        Map<String, Station> result = new LinkedHashMap<>();
        List<String> codes = newStops.stream().map(StopRequest::stationCode).distinct().sorted().toList();
        for (String code : codes) {
            result.put(code, stations.findByCodeForUpdate(code)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "车站不存在: " + code)));
        }
        return result;
    }

    /** 区间约束：同时占用数不超过通过能力，且与既有占用满足最小追踪间隔。 */
    private void checkSectionConstraints(List<LegRequest> newLegs, Map<String, Section> sectionMap, Long excludePathId) {
        List<Long> sectionIds = sectionMap.values().stream().map(Section::getId).toList();
        List<PathLeg> existing = legs.findBySectionIdIn(sectionIds);
        for (LegRequest newLeg : newLegs) {
            Section section = sectionMap.get(newLeg.sectionCode());
            List<PathLeg> onSection = existing.stream()
                    .filter(l -> l.getSection().getId().equals(section.getId()))
                    .filter(l -> excludePathId == null || !l.getPath().getId().equals(excludePathId))
                    .toList();
            long overlapping = onSection.stream()
                    .filter(l -> l.getEnterTime().isBefore(newLeg.exitTime())
                            && newLeg.enterTime().isBefore(l.getExitTime()))
                    .count();
            if (overlapping >= section.getCapacity()) {
                throw new ApiException(ErrorCode.CAPACITY_EXCEEDED,
                        "区间 " + section.getCode() + " 同时占用数将达到通过能力上限 " + section.getCapacity());
            }
            long headway = section.getMinHeadwayMinutes();
            for (PathLeg other : onSection) {
                long enterGap = Math.abs(Duration.between(other.getEnterTime(), newLeg.enterTime()).toMinutes());
                long exitGap = Math.abs(Duration.between(other.getExitTime(), newLeg.exitTime()).toMinutes());
                if (enterGap < headway || exitGap < headway) {
                    throw new ApiException(ErrorCode.HEADWAY_VIOLATION,
                            "区间 " + section.getCode() + " 不满足最小追踪间隔 " + headway + " 分钟");
                }
            }
        }
    }

    /** 车站约束：同时停靠数不超过可用到发线数量。 */
    private void checkStationConstraints(List<StopRequest> newStops, Map<String, Station> stationMap, Long excludePathId) {
        if (newStops.isEmpty()) {
            return;
        }
        List<Long> stationIds = stationMap.values().stream().map(Station::getId).toList();
        List<PathStop> existing = stops.findByStationIdIn(stationIds);
        for (StopRequest newStop : newStops) {
            Station station = stationMap.get(newStop.stationCode());
            long overlapping = existing.stream()
                    .filter(s -> s.getStation().getId().equals(station.getId()))
                    .filter(s -> excludePathId == null || !s.getPath().getId().equals(excludePathId))
                    .filter(s -> s.getArriveTime().isBefore(newStop.departTime())
                            && newStop.arriveTime().isBefore(s.getDepartTime()))
                    .count();
            if (overlapping >= station.getTrackCount()) {
                throw new ApiException(ErrorCode.CAPACITY_EXCEEDED,
                        "车站 " + station.getCode() + " 到发线不足（上限 " + station.getTrackCount() + " 条）");
            }
        }
    }

    private String contentHash(List<LegRequest> newLegs, List<StopRequest> newStops) {
        StringBuilder sb = new StringBuilder();
        for (LegRequest leg : newLegs) {
            sb.append('L').append(leg.sectionCode()).append('|')
                    .append(leg.enterTime()).append('|').append(leg.exitTime()).append(';');
        }
        for (StopRequest stop : newStops) {
            sb.append('S').append(stop.stationCode()).append('|')
                    .append(stop.arriveTime()).append('|').append(stop.departTime()).append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String snapshot(TrainPath path) {
        List<String> parts = new ArrayList<>();
        for (PathLeg leg : path.getLegs()) {
            parts.add("leg " + leg.getSection().getCode() + " " + leg.getEnterTime() + "~" + leg.getExitTime());
        }
        for (PathStop stop : path.getStops()) {
            parts.add("stop " + stop.getStation().getCode() + " " + stop.getArriveTime() + "~" + stop.getDepartTime());
        }
        return String.join("; ", parts);
    }

    private PathResponse toResponse(TrainPath path) {
        List<LegView> legViews = path.getLegs().stream()
                .map(l -> new LegView(l.getSection().getCode(), l.getEnterTime(), l.getExitTime()))
                .toList();
        List<StopView> stopViews = path.getStops().stream()
                .map(s -> new StopView(s.getStation().getCode(), s.getArriveTime(), s.getDepartTime()))
                .toList();
        return new PathResponse(path.getExternalRef(), path.getStatus().name(), legViews, stopViews);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
