package com.chris64233.railpath.service;

import com.chris64233.railpath.api.LegRequest;
import com.chris64233.railpath.domain.Direction;
import com.chris64233.railpath.domain.RailSection;
import com.chris64233.railpath.error.ApiException;
import com.chris64233.railpath.error.ErrorType;
import com.chris64233.railpath.repository.PathEventRepository;
import com.chris64233.railpath.repository.PathLegRepository;
import com.chris64233.railpath.repository.PathReservationRepository;
import com.chris64233.railpath.repository.RailSectionRepository;
import com.chris64233.railpath.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.chris64233.railpath.TestFixtures.single;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 改线发车门槛测试。使用固定时钟，使“当前时刻”确定可控。
 */
@SpringBootTest
class RerouteDepartureTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private PathService pathService;
    @Autowired
    private RailSectionRepository sectionRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private PathLegRepository legRepository;
    @Autowired
    private PathReservationRepository reservationRepository;
    @Autowired
    private PathEventRepository eventRepository;

    @BeforeEach
    void clean() {
        legRepository.deleteAll();
        eventRepository.deleteAll();
        reservationRepository.deleteAll();
        sectionRepository.deleteAll();
        stationRepository.deleteAll();
        sectionRepository.save(new RailSection("S1", Direction.UP, 1, 0));
    }

    @Test
    void reroute_is_rejected_once_train_has_departed() {
        // 首段进入时间早于固定当前时间 => 已发车。
        pathService.approve(single("R1", "S1",
                NOW.minusSeconds(3600), NOW.minusSeconds(3000)));

        List<LegRequest> newPath = List.of(
                new LegRequest("S1", NOW.plusSeconds(600), NOW.plusSeconds(1200), null));
        assertThatThrownBy(() -> pathService.reroute("R1", newPath))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    void reroute_is_allowed_before_departure() {
        // 首段进入时间在固定当前时间之后 => 尚未发车。
        pathService.approve(single("R1", "S1",
                NOW.plusSeconds(3600), NOW.plusSeconds(4200)));

        List<LegRequest> newPath = List.of(
                new LegRequest("S1", NOW.plusSeconds(3600), NOW.plusSeconds(4000), null));
        assertThat(pathService.reroute("R1", newPath).status()).isEqualTo("ACTIVE");
    }
}
