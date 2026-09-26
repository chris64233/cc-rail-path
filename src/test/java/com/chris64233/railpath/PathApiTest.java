package com.chris64233.railpath;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.chris64233.railpath.repository.PathLegRepository;
import com.chris64233.railpath.repository.PathStopRepository;
import com.chris64233.railpath.repository.SectionRepository;
import com.chris64233.railpath.repository.StationRepository;
import com.chris64233.railpath.repository.TrainPathRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PathApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TrainPathRepository paths;
    @Autowired
    SectionRepository sections;
    @Autowired
    StationRepository stations;
    @Autowired
    PathLegRepository legs;
    @Autowired
    PathStopRepository stops;

    @BeforeEach
    void clean() {
        paths.deleteAll();
        sections.deleteAll();
        stations.deleteAll();
    }

    // ---- 基础资源与申请 ----

    @Test
    void applyApprovedAndOccupancyDetailQueryable() throws Exception {
        createSection("S1", "UP", 2, 5);
        createSection("S2", "UP", 2, 5);
        createStation("ST1", 2);

        String body = """
                {
                  "externalRef": "T-1001",
                  "legs": [
                    {"sectionCode": "S1", "enterTime": "2030-06-01T08:00:00", "exitTime": "2030-06-01T08:20:00"},
                    {"sectionCode": "S2", "enterTime": "2030-06-01T08:20:00", "exitTime": "2030-06-01T08:45:00"}
                  ],
                  "stops": [
                    {"stationCode": "ST1", "arriveTime": "2030-06-01T08:10:00", "departTime": "2030-06-01T08:12:00"}
                  ]
                }
                """;
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalRef").value("T-1001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.legs", hasSize(2)))
                .andExpect(jsonPath("$.stops", hasSize(1)));

        mvc.perform(get("/api/paths/T-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].sectionCode").value("S1"))
                .andExpect(jsonPath("$.legs[1].sectionCode").value("S2"))
                .andExpect(jsonPath("$.stops[0].stationCode").value("ST1"));

        mvc.perform(get("/api/paths/T-1001/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("APPROVED"));
    }

    @Test
    void applyRejectsInvalidArguments() throws Exception {
        createSection("S1", "UP", 2, 5);
        // 缺少运行号
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("""
                {"legs": [{"sectionCode": "S1", "enterTime": "2030-06-01T08:00:00", "exitTime": "2030-06-01T08:20:00"}]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void applyRejectsNonChronologicalTimeline() throws Exception {
        createSection("S1", "UP", 2, 5);
        createSection("S2", "UP", 2, 5);
        // 单区间进入时间晚于离开时间
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "externalRef": "T-BAD-1",
                  "legs": [{"sectionCode": "S1", "enterTime": "2030-06-01T08:20:00", "exitTime": "2030-06-01T08:00:00"}]
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 相邻区间时间重叠，径路不连续
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "externalRef": "T-BAD-2",
                  "legs": [
                    {"sectionCode": "S1", "enterTime": "2030-06-01T08:00:00", "exitTime": "2030-06-01T08:20:00"},
                    {"sectionCode": "S2", "enterTime": "2030-06-01T08:10:00", "exitTime": "2030-06-01T08:30:00"}
                  ]
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void applyRejectsStopOutsidePathWindow() throws Exception {
        createSection("S1", "UP", 2, 5);
        createStation("ST1", 2);
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "externalRef": "T-BAD-3",
                  "legs": [{"sectionCode": "S1", "enterTime": "2030-06-01T08:00:00", "exitTime": "2030-06-01T08:20:00"}],
                  "stops": [{"stationCode": "ST1", "arriveTime": "2030-06-01T09:00:00", "departTime": "2030-06-01T09:10:00"}]
                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void applyWithUnknownResourceReturnsNotFound() throws Exception {
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "externalRef": "T-404",
                  "legs": [{"sectionCode": "NOPE", "enterTime": "2030-06-01T08:00:00", "exitTime": "2030-06-01T08:20:00"}]
                }
                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---- 容量与追踪间隔 ----

    @Test
    void headwayViolationIsRejected() throws Exception {
        createSection("S1", "UP", 5, 10);
        applyOk("T-2001", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");
        // 进入时间仅间隔 5 分钟，小于最小追踪间隔 10 分钟
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(
                applyBody("T-2002", "S1", "2030-06-01T08:05:00", "2030-06-01T08:25:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HEADWAY_VIOLATION"));
    }

    @Test
    void sectionCapacityIsEnforced() throws Exception {
        createSection("S1", "UP", 1, 0);
        applyOk("T-3001", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(
                applyBody("T-3002", "S1", "2030-06-01T08:10:00", "2030-06-01T08:30:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
        // 不重叠的申请可以通过
        applyOk("T-3003", "S1", "2030-06-01T08:20:00", "2030-06-01T08:40:00");
    }

    @Test
    void stationTrackCapacityIsEnforced() throws Exception {
        createSection("S1", "UP", 10, 0);
        createStation("ST1", 1);
        applyWithStop("T-4001", "ST1", "2030-06-01T08:05:00", "2030-06-01T08:15:00");
        // 同一车站同一时间窗第二列车超出到发线数量
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(
                applyWithStopBody("T-4002", "ST1", "2030-06-01T08:10:00", "2030-06-01T08:18:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
    }

    @Test
    void failedApplyLeavesNoPartialOccupancy() throws Exception {
        createSection("S1", "UP", 2, 0);
        createSection("S2", "UP", 1, 0);
        // 先占满 S2
        applyOk("T-5001", "S2", "2030-06-01T08:20:00", "2030-06-01T08:45:00");
        // 申请第一段合法、第二段在 S2 上冲突
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content("""
                {
                  "externalRef": "T-5002",
                  "legs": [
                    {"sectionCode": "S1", "enterTime": "2030-06-01T08:00:00", "exitTime": "2030-06-01T08:20:00"},
                    {"sectionCode": "S2", "enterTime": "2030-06-01T08:20:00", "exitTime": "2030-06-01T08:45:00"}
                  ]
                }
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));
        // 不能留下部分径路：T-5002 不存在，且只有 T-5001 的一条占用
        mvc.perform(get("/api/paths/T-5002")).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(paths.findAll()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(legs.findAll()).hasSize(1);
    }

    // ---- 幂等 ----

    @Test
    void idempotentReplayReturnsOriginalResult() throws Exception {
        createSection("S1", "UP", 2, 0);
        String body = applyBody("T-6001", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        // 相同编号相同内容重放：返回原结果
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalRef").value("T-6001"))
                .andExpect(jsonPath("$.legs", hasSize(1)));
        // 仍然只有一条径路
        org.assertj.core.api.Assertions.assertThat(paths.findAll()).hasSize(1);
    }

    @Test
    void sameRefWithDifferentContentConflicts() throws Exception {
        createSection("S1", "UP", 2, 0);
        applyOk("T-6002", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(
                applyBody("T-6002", "S1", "2030-06-01T09:00:00", "2030-06-01T09:20:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ---- 改线 ----

    @Test
    void rerouteReplacesOccupancyAtomically() throws Exception {
        createSection("S1", "UP", 1, 0);
        createSection("S2", "UP", 1, 0);
        applyOk("T-7001", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");

        mvc.perform(post("/api/paths/T-7001/reroute").contentType(MediaType.APPLICATION_JSON).content("""
                {"legs": [{"sectionCode": "S2", "enterTime": "2030-06-01T09:00:00", "exitTime": "2030-06-01T09:30:00"}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].sectionCode").value("S2"));

        // 旧径路已释放：其他列车可以占用 S1 原时间窗
        applyOk("T-7002", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");

        mvc.perform(get("/api/paths/T-7001/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", contains("APPROVED", "REROUTED")));
    }

    @Test
    void failedRerouteKeepsOriginalPlan() throws Exception {
        createSection("S1", "UP", 1, 0);
        createSection("S2", "UP", 1, 0);
        applyOk("T-7101", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");
        applyOk("T-7102", "S2", "2030-06-01T09:00:00", "2030-06-01T09:30:00");

        // 改线到已被占用的 S2 同一时间窗：失败，原计划不变
        mvc.perform(post("/api/paths/T-7101/reroute").contentType(MediaType.APPLICATION_JSON).content("""
                {"legs": [{"sectionCode": "S2", "enterTime": "2030-06-01T09:00:00", "exitTime": "2030-06-01T09:30:00"}]}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_EXCEEDED"));

        mvc.perform(get("/api/paths/T-7101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].sectionCode").value("S1"))
                .andExpect(jsonPath("$.legs[0].enterTime").value("2030-06-01T08:00:00"));
    }

    @Test
    void rerouteAfterDepartureIsRejected() throws Exception {
        createSection("S1", "UP", 1, 0);
        createSection("S2", "UP", 1, 0);
        // 已经发车的径路（进入时间在过去）
        applyOk("T-7201", "S1", "2020-01-01T08:00:00", "2020-01-01T08:20:00");
        mvc.perform(post("/api/paths/T-7201/reroute").contentType(MediaType.APPLICATION_JSON).content("""
                {"legs": [{"sectionCode": "S2", "enterTime": "2030-06-01T09:00:00", "exitTime": "2030-06-01T09:30:00"}]}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // ---- 取消 ----

    @Test
    void cancelReleasesOccupancyAndRecordsEvents() throws Exception {
        createSection("S1", "UP", 1, 0);
        applyOk("T-8001", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");

        mvc.perform(post("/api/paths/T-8001/cancel")).andExpect(status().isNoContent());

        // 占用已释放：其他列车可以进入原时间窗
        applyOk("T-8002", "S1", "2030-06-01T08:00:00", "2030-06-01T08:20:00");

        mvc.perform(get("/api/paths/T-8001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.legs", hasSize(0)));

        mvc.perform(get("/api/paths/T-8001/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", contains("APPROVED", "CANCELLED")));

        // 重复取消返回状态错误
        mvc.perform(post("/api/paths/T-8001/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    // ---- 辅助方法 ----

    private void createSection(String code, String direction, int capacity, int headway) throws Exception {
        mvc.perform(post("/api/sections").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "direction": "%s", "capacity": %d, "minHeadwayMinutes": %d}
                """.formatted(code, direction, capacity, headway)))
                .andExpect(status().isCreated());
    }

    private void createStation(String code, int trackCount) throws Exception {
        mvc.perform(post("/api/stations").contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "%s", "trackCount": %d}
                """.formatted(code, trackCount)))
                .andExpect(status().isCreated());
    }

    private String applyBody(String ref, String section, String enter, String exit) {
        return """
                {
                  "externalRef": "%s",
                  "legs": [{"sectionCode": "%s", "enterTime": "%s", "exitTime": "%s"}]
                }
                """.formatted(ref, section, enter, exit);
    }

    private void applyOk(String ref, String section, String enter, String exit) throws Exception {
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
                .content(applyBody(ref, section, enter, exit)))
                .andExpect(status().isCreated());
    }

    private String applyWithStopBody(String ref, String station, String arrive, String depart) {
        return """
                {
                  "externalRef": "%s",
                  "legs": [{"sectionCode": "S1", "enterTime": "2030-06-01T08:00:00", "exitTime": "2030-06-01T08:30:00"}],
                  "stops": [{"stationCode": "%s", "arriveTime": "%s", "departTime": "%s"}]
                }
                """.formatted(ref, station, arrive, depart);
    }

    private void applyWithStop(String ref, String station, String arrive, String depart) throws Exception {
        mvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
                .content(applyWithStopBody(ref, station, arrive, depart)))
                .andExpect(status().isCreated());
    }
}
