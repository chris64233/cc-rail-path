package com.chris64233.railpath.api;

import com.chris64233.railpath.repository.PathEventRepository;
import com.chris64233.railpath.repository.PathLegRepository;
import com.chris64233.railpath.repository.PathReservationRepository;
import com.chris64233.railpath.repository.RailSectionRepository;
import com.chris64233.railpath.repository.StationRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 接口与统一错误信封的端到端测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PathControllerWebTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RailSectionRepository sectionRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private PathReservationRepository reservationRepository;
    @Autowired
    private PathLegRepository legRepository;
    @Autowired
    private PathEventRepository eventRepository;

    @BeforeEach
    void clean() {
        legRepository.deleteAll();
        eventRepository.deleteAll();
        reservationRepository.deleteAll();
        sectionRepository.deleteAll();
        stationRepository.deleteAll();
    }

    @Test
    void registers_section_and_station() throws Exception {
        mockMvc.perform(post("/api/resources/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"S1","direction":"UP","capacity":2,"minHeadwaySeconds":300}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S1"))
                .andExpect(jsonPath("$.capacity").value(2));

        mockMvc.perform(post("/api/resources/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ST-A","trackCount":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackCount").value(3));
    }

    @Test
    void validation_error_uses_unified_envelope() throws Exception {
        mockMvc.perform(post("/api/resources/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","direction":"UP","capacity":0,"minHeadwaySeconds":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"))
                .andExpect(jsonPath("$.fields[0].field").exists());
    }

    @Test
    void zero_headway_is_a_valid_section() throws Exception {
        mockMvc.perform(post("/api/resources/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"S0","direction":"DOWN","capacity":1,"minHeadwaySeconds":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minHeadwaySeconds").value(0));
    }

    @Test
    void approve_returns_full_occupancy_and_events() throws Exception {
        seedSection("S1", 1, 0);
        String body = """
                {"externalRunNo":"W1","legs":[
                  {"sectionCode":"S1","entryTime":"2026-10-01T08:00:00Z","exitTime":"2026-10-01T08:10:00Z","stopStationCode":null}
                ]}
                """;
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalRunNo").value("W1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.occupancies[0].resourceCode").value("S1"));

        mockMvc.perform(get("/api/paths/W1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("APPROVED"));
    }

    @Test
    void capacity_conflict_returns_409_with_detail() throws Exception {
        seedSection("S1", 1, 0);
        String first = pathBody("W1", "2026-10-01T08:00:00Z", "2026-10-01T08:20:00Z");
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());

        String second = pathBody("W2", "2026-10-01T08:10:00Z", "2026-10-01T08:30:00Z");
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.conflicts[0].resourceCode").value("S1"))
                .andExpect(jsonPath("$.conflicts[0].conflictingRunNos[0]").value("W1"));
    }

    @Test
    void same_run_no_different_content_returns_409() throws Exception {
        seedSection("S1", 1, 0);
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
                        .content(pathBody("W1", "2026-10-01T08:00:00Z", "2026-10-01T08:20:00Z")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
                        .content(pathBody("W1", "2026-10-01T09:00:00Z", "2026-10-01T09:20:00Z")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void replay_same_content_returns_original() throws Exception {
        seedSection("S1", 1, 0);
        String body = pathBody("W1", "2026-10-01T08:00:00Z", "2026-10-01T08:20:00Z");
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalRunNo").value("W1"));
    }

    @Test
    void unknown_path_returns_404() throws Exception {
        mockMvc.perform(get("/api/paths/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cancel_and_reroute_emit_events() throws Exception {
        seedSection("S1", 1, 0);
        seedSection("S2", 1, 0);
        mockMvc.perform(post("/api/paths").contentType(MediaType.APPLICATION_JSON)
                        .content(pathBody("W1", "2026-10-01T08:00:00Z", "2026-10-01T08:20:00Z")))
                .andExpect(status().isCreated());

        // 改到尚未占用的 S2，时间在未来。
        String reroute = """
                {"legs":[
                  {"sectionCode":"S2","entryTime":"2026-10-01T08:00:00Z","exitTime":"2026-10-01T08:20:00Z","stopStationCode":null}
                ]}
                """;
        mockMvc.perform(post("/api/paths/W1/reroute")
                        .contentType(MediaType.APPLICATION_JSON).content(reroute))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupancies[0].resourceCode").value("S2"));

        mockMvc.perform(post("/api/paths/W1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/paths/W1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("APPROVED"))
                .andExpect(jsonPath("$[1].eventType").value("REROUTED"))
                .andExpect(jsonPath("$[2].eventType").value("CANCELLED"));
    }

    private void seedSection(String code, int capacity, long headway) {
        sectionRepository.save(new com.chris64233.railpath.domain.RailSection(
                code, com.chris64233.railpath.domain.Direction.UP, capacity, headway));
    }

    private String pathBody(String runNo, String entry, String exit) {
        return """
                {"externalRunNo":"%s","legs":[
                  {"sectionCode":"S1","entryTime":"%s","exitTime":"%s","stopStationCode":null}
                ]}
                """.formatted(runNo, entry, exit);
    }
}
