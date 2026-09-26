package com.chris64233.railpath.service;

import com.chris64233.railpath.domain.LegType;
import com.chris64233.railpath.domain.PathLeg;
import com.chris64233.railpath.domain.PathReservation;
import com.chris64233.railpath.domain.RailSection;
import com.chris64233.railpath.domain.Station;
import com.chris64233.railpath.error.ApiException;
import com.chris64233.railpath.error.ConflictDetail;
import com.chris64233.railpath.error.ErrorType;
import com.chris64233.railpath.repository.PathLegRepository;
import com.chris64233.railpath.repository.PathReservationRepository;
import com.chris64233.railpath.repository.RailSectionRepository;
import com.chris64233.railpath.repository.StationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 径路可行性把关：对申请涉及的区间、车站资源行加悲观写锁，
 * 再在最新占用数据上检查通过能力、到发线容量与最小追踪间隔。
 *
 * <p>所有调用方都处于单个事务内；资源按“先全部区间（代码升序）、再全部车站（代码升序）”
 * 的全局固定顺序加锁，避免多事务交叉加锁造成死锁。
 */
@Component
public class FeasibilityGuard {

    /** 加锁并完成权威判定后的结果。 */
    public enum ApprovalVerdict {
        /** 资源均可行，可写入新径路。 */
        FEASIBLE,
        /** 相同运行号、相同内容的径路已（被并发事务）提交，应回放原结果。 */
        REPLAY
    }

    /** 批准校验结果，REPLAY 时携带已存在的径路。 */
    public record ApprovalResult(ApprovalVerdict verdict, PathReservation existing) {
    }

    private final RailSectionRepository sectionRepository;
    private final StationRepository stationRepository;
    private final PathLegRepository legRepository;
    private final PathReservationRepository reservationRepository;

    public FeasibilityGuard(RailSectionRepository sectionRepository,
                            StationRepository stationRepository,
                            PathLegRepository legRepository,
                            PathReservationRepository reservationRepository) {
        this.sectionRepository = sectionRepository;
        this.stationRepository = stationRepository;
        this.legRepository = legRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * 批准场景：加锁后先做权威的同号幂等判定（覆盖并发提交），再检查容量。
     *
     * <p>必须在资源锁持有期间再次按运行号读取：两个并发的相同申请可能在加锁前都查不到彼此，
     * 只有在锁上排队、前者提交后，后者才能读到已提交径路并回放，而不是误报容量冲突。
     */
    @Transactional
    public ApprovalResult verifyForApproval(List<Plans.Occupancy> candidate,
                                            String externalRunNo, String requestHash) {
        lockResources(candidate);
        PathReservation existing = reservationRepository.findByExternalRunNo(externalRunNo).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ApiException(ErrorType.CONFLICT, "CONFLICT_RUN_NO_CONTENT_MISMATCH",
                        "外部运行号 " + externalRunNo + " 已存在，但申请内容与原申请不同");
            }
            return new ApprovalResult(ApprovalVerdict.REPLAY, existing);
        }
        checkCapacity(candidate, null);
        return new ApprovalResult(ApprovalVerdict.FEASIBLE, null);
    }

    /**
     * 改线场景：加锁后检查新径路容量，校验时排除该径路自身的旧占用。
     */
    @Transactional
    public void verifyForReroute(List<Plans.Occupancy> candidate, Long selfReservationId) {
        lockResources(candidate);
        checkCapacity(candidate, selfReservationId);
    }

    private void lockResources(List<Plans.Occupancy> candidate) {
        // 固定顺序：先全部区间（升序），再全部车站（升序）。
        lockSections(candidate);
        lockStations(candidate);
    }

    private void lockSections(List<Plans.Occupancy> candidate) {
        TreeSet<String> codes = candidate.stream()
                .filter(o -> o.type() == LegType.SECTION)
                .map(Plans.Occupancy::code)
                .collect(Collectors.toCollection(TreeSet::new));
        if (codes.isEmpty()) {
            return;
        }
        List<RailSection> locked = sectionRepository.findByCodeInForUpdate(codes);
        Map<String, RailSection> map = locked.stream()
                .collect(Collectors.toMap(RailSection::getCode, s -> s));
        List<String> missing = codes.stream().filter(c -> !map.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw resourceNotFound("SECTION", missing);
        }
    }

    private void lockStations(List<Plans.Occupancy> candidate) {
        TreeSet<String> codes = candidate.stream()
                .filter(o -> o.type() == LegType.STATION)
                .map(Plans.Occupancy::code)
                .collect(Collectors.toCollection(TreeSet::new));
        if (codes.isEmpty()) {
            return;
        }
        List<Station> locked = stationRepository.findByCodeInForUpdate(codes);
        Map<String, Station> map = locked.stream()
                .collect(Collectors.toMap(Station::getCode, s -> s));
        List<String> missing = codes.stream().filter(c -> !map.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw resourceNotFound("STATION", missing);
        }
    }

    /** 对候选涉及的每个资源检查容量与追踪间隔，收集并一次性抛出全部冲突。 */
    private void checkCapacity(List<Plans.Occupancy> candidate, Long excludeId) {
        List<ConflictDetail> conflicts = new ArrayList<>();

        candidate.stream().filter(o -> o.type() == LegType.SECTION)
                .map(Plans.Occupancy::code).collect(Collectors.toCollection(TreeSet::new))
                .forEach(code -> {
                    RailSection section = sectionRepository.findById(code).orElseThrow();
                    checkSection(section, candidate, excludeId, conflicts);
                });
        candidate.stream().filter(o -> o.type() == LegType.STATION)
                .map(Plans.Occupancy::code).collect(Collectors.toCollection(TreeSet::new))
                .forEach(code -> {
                    Station station = stationRepository.findById(code).orElseThrow();
                    checkStation(station, candidate, excludeId, conflicts);
                });

        if (!conflicts.isEmpty()) {
            throw new ApiException(ErrorType.CAPACITY_EXCEEDED, "CAPACITY_CONFLICT",
                    "径路占用与既有计划冲突，超过区间/车站容量或违反最小追踪间隔", conflicts);
        }
    }

    private void checkSection(RailSection section, List<Plans.Occupancy> candidate,
                              Long excludeId, List<ConflictDetail> conflicts) {
        List<Plans.Occupancy> mine = candidate.stream()
                .filter(o -> o.type() == LegType.SECTION && o.code().equals(section.getCode()))
                .toList();
        Instant windowStart = mine.stream().map(Plans.Occupancy::entry).min(Instant::compareTo).orElseThrow();
        Instant windowEnd = mine.stream().map(Plans.Occupancy::exit).max(Instant::compareTo).orElseThrow();

        // 容量检查只需要时间窗内重叠的既有占用。
        List<PathLeg> overlapping = legRepository.findActiveOverlapping(
                LegType.SECTION, section.getCode(), windowStart, windowEnd, excludeId);
        checkCapacity(LegType.SECTION, section.getCode(), section.getCapacity(),
                windowStart, windowEnd, mine, overlapping, conflicts);

        // 追踪间隔按“进入时刻”判定：即使占用时间窗不重叠，进入时刻间隔过小也算冲突。
        long headway = section.getMinHeadwaySeconds();
        Instant earliestEntry = mine.stream().map(Plans.Occupancy::entry).min(Instant::compareTo).orElseThrow();
        Instant latestEntry = mine.stream().map(Plans.Occupancy::entry).max(Instant::compareTo).orElseThrow();
        List<PathLeg> nearEntry = legRepository.findActiveByEntryTimeBetween(
                LegType.SECTION, section.getCode(),
                earliestEntry.minusSeconds(headway), latestEntry.plusSeconds(headway), excludeId);
        checkHeadway(section, mine, nearEntry, conflicts);
    }

    private void checkStation(Station station, List<Plans.Occupancy> candidate,
                              Long excludeId, List<ConflictDetail> conflicts) {
        List<Plans.Occupancy> mine = candidate.stream()
                .filter(o -> o.type() == LegType.STATION && o.code().equals(station.getCode()))
                .toList();
        Instant windowStart = mine.stream().map(Plans.Occupancy::entry).min(Instant::compareTo).orElseThrow();
        Instant windowEnd = mine.stream().map(Plans.Occupancy::exit).max(Instant::compareTo).orElseThrow();
        List<PathLeg> existing = legRepository.findActiveOverlapping(
                LegType.STATION, station.getCode(), windowStart, windowEnd, excludeId);

        checkCapacity(LegType.STATION, station.getCode(), station.getTrackCount(),
                windowStart, windowEnd, mine, existing, conflicts);
    }

    /**
     * 时间轴扫描：把候选占用与既有占用放在同一时间轴上，
     * 任意时刻（左闭右开）占用数超过容量即冲突。
     */
    private void checkCapacity(LegType type, String code, int capacity,
                               Instant windowStart, Instant windowEnd,
                               List<Plans.Occupancy> mine, List<PathLeg> existing,
                               List<ConflictDetail> conflicts) {
        // 端点：同一时刻离开（-1）排在进入（+1）之前，体现左闭右开。
        List<Endpoint> endpoints = new ArrayList<>();
        mine.forEach(o -> {
            endpoints.add(new Endpoint(o.entry(), 1, null));
            endpoints.add(new Endpoint(o.exit(), -1, null));
        });
        existing.forEach(l -> {
            String runNo = l.getReservation().getExternalRunNo();
            endpoints.add(new Endpoint(l.getEntryTime(), 1, runNo));
            endpoints.add(new Endpoint(l.getExitTime(), -1, runNo));
        });
        endpoints.sort(Comparator.comparing(Endpoint::time).thenComparingInt(Endpoint::delta));

        int peak = 0;
        Instant peakTime = null;
        java.util.Set<String> peakRunNos = java.util.Set.of();
        java.util.Set<String> activeRuns = new java.util.HashSet<>();
        int level = 0;
        int i = 0;
        while (i < endpoints.size()) {
            Instant t = endpoints.get(i).time();
            // 应用该时刻的全部变化（先释放后进入），得到 [t, 下一时刻) 区间内的占用。
            while (i < endpoints.size() && endpoints.get(i).time().equals(t)) {
                Endpoint e = endpoints.get(i);
                level += e.delta();
                if (e.runNo() != null) {
                    if (e.delta() < 0) {
                        activeRuns.remove(e.runNo());
                    } else {
                        activeRuns.add(e.runNo());
                    }
                }
                i++;
            }
            if (level > peak) {
                peak = level;
                peakTime = t;
                peakRunNos = new java.util.LinkedHashSet<>(activeRuns);
            }
        }
        if (peak > capacity) {
            String reason = type == LegType.SECTION
                    ? "超过区间通过能力：时刻 " + peakTime + " 同时占用 " + peak + " 列，能力为 " + capacity
                    : "超过车站到发线数量：时刻 " + peakTime + " 同时停靠 " + peak + " 列，到发线 " + capacity + " 条";
            conflicts.add(new ConflictDetail(type.name(), code, reason,
                    windowStart.toString(), windowEnd.toString(),
                    new ArrayList<>(peakRunNos)));
        }
    }

    /**
     * 追踪间隔：候选占用与每个既有占用的进入时刻之差都不得小于最小追踪间隔。
     * 候选之间属于同一列车，不做此检查。
     */
    private void checkHeadway(RailSection section, List<Plans.Occupancy> mine,
                              List<PathLeg> existing, List<ConflictDetail> conflicts) {
        long minHeadway = section.getMinHeadwaySeconds();
        for (Plans.Occupancy o : mine) {
            List<String> violatingRuns = new ArrayList<>();
            long nearestGap = Long.MAX_VALUE;
            for (PathLeg other : existing) {
                long gap = Math.abs(Duration.between(o.entry(), other.getEntryTime()).getSeconds());
                if (gap < minHeadway) {
                    violatingRuns.add(other.getReservation().getExternalRunNo());
                    nearestGap = Math.min(nearestGap, gap);
                }
            }
            if (!violatingRuns.isEmpty()) {
                conflicts.add(new ConflictDetail(
                        LegType.SECTION.name(), section.getCode(),
                        "违反最小追踪间隔：进入时刻 " + o.entry() + " 与既有列车仅相差 "
                                + nearestGap + " 秒，要求至少 " + minHeadway + " 秒",
                        o.entry().toString(), o.exit().toString(),
                        distinct(violatingRuns)));
            }
        }
    }

    private static List<String> distinct(Collection<String> values) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(values));
    }

    private static ApiException resourceNotFound(String type, List<String> codes) {
        return new ApiException(ErrorType.RESOURCE_NOT_FOUND, "RESOURCE_NOT_FOUND",
                "引用的" + ("SECTION".equals(type) ? "区间" : "车站") + "不存在：" + codes);
    }

    private record Endpoint(Instant time, int delta, String runNo) {
    }
}
