package com.chris64233.railpath;

import com.chris64233.railpath.api.LegRequest;
import com.chris64233.railpath.api.PathRequest;
import com.chris64233.railpath.api.SectionRequest;
import com.chris64233.railpath.api.StationRequest;
import com.chris64233.railpath.domain.Direction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据构造辅助。
 */
public final class TestFixtures {

    public static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    private TestFixtures() {
    }

    public static SectionRequest section(String code, int capacity, long headwaySeconds) {
        return new SectionRequest(code, Direction.UP, capacity, headwaySeconds);
    }

    public static StationRequest station(String code, int trackCount) {
        return new StationRequest(code, trackCount);
    }

    public static LegRequest leg(String section, Instant entry, Instant exit, String stopStation) {
        return new LegRequest(section, entry, exit, stopStation);
    }

    /** 单区间径路。 */
    public static PathRequest single(String runNo, String section, Instant entry, Instant exit) {
        return new PathRequest(runNo, List.of(leg(section, entry, exit, null)));
    }

    /**
     * 顺序经过多个区间，区间之间在中间站停靠。
     * 每段行驶 10 分钟，中间站停靠 5 分钟。
     */
    public static PathRequest chain(String runNo, List<String> sections, List<String> stations,
                                    Instant start, long runMinutes, long dwellMinutes) {
        List<LegRequest> legs = new ArrayList<>();
        Instant cursor = start;
        for (int i = 0; i < sections.size(); i++) {
            Instant exit = cursor.plusSeconds(runMinutes * 60);
            boolean last = i == sections.size() - 1;
            String stop = last ? null : stations.get(i);
            legs.add(leg(sections.get(i), cursor, exit, stop));
            cursor = last ? exit : exit.plusSeconds(dwellMinutes * 60);
        }
        return new PathRequest(runNo, legs);
    }
}
