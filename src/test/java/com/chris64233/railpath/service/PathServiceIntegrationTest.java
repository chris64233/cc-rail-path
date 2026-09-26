package com.chris64233.railpath.service;

import com.chris64233.railpath.TestFixtures;
import com.chris64233.railpath.api.LegRequest;
import com.chris64233.railpath.api.PathRequest;
import com.chris64233.railpath.api.ReservationView;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.chris64233.railpath.TestFixtures.T0;
import static com.chris64233.railpath.TestFixtures.chain;
import static com.chris64233.railpath.TestFixtures.leg;
import static com.chris64233.railpath.TestFixtures.single;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 径路批准、改线、取消与并发容量的核心业务测试。
 */
@SpringBootTest
class PathServiceIntegrationTest {

    @Autowired
    private PathService pathService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private PathReservationRepository reservationRepository;
    @Autowired
    private PathLegRepository legRepository;
    @Autowired
    private RailSectionRepository sectionRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private PathEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        legRepository.deleteAll();
        eventRepository.deleteAll();
        reservationRepository.deleteAll();
        sectionRepository.deleteAll();
        stationRepository.deleteAll();
    }

    private void sections(String... codes) {
        for (String code : codes) {
            resourceService.registerSection(TestFixtures.section(code, 1, 600));
        }
    }

    private void stations(String... codes) {
        for (String code : codes) {
            resourceService.registerStation(TestFixtures.station(code, 1));
        }
    }

    // ---------- 批准与连续性 ----------

    @Test
    void approve_writes_all_section_and_station_occupancies_in_order() {
        resourceService.registerSection(TestFixtures.section("S1", 1, 0));
        resourceService.registerSection(TestFixtures.section("S2", 1, 0));
        resourceService.registerStation(TestFixtures.station("ST-A", 2));

        ReservationView view = pathService.approve(
                chain("R1", List.of("S1", "S2"), List.of("ST-A"), T0, 10, 5));

        assertThat(view.status()).isEqualTo("ACTIVE");
        // 2 个区间段 + 1 个中间站停靠。
        assertThat(view.occupancies()).hasSize(3);
        assertThat(view.occupancies()).extracting(o -> o.resourceType() + ":" + o.resourceCode())
                .containsExactly("SECTION:S1", "STATION:ST-A", "SECTION:S2");
        assertThat(view.occupancies()).extracting(o -> o.seq()).containsExactly(0, 1, 2);
        // 时间首尾相接。
        assertThat(view.occupancies().get(0).exitTime()).isEqualTo(view.occupancies().get(1).entryTime());
        assertThat(view.occupancies().get(1).exitTime()).isEqualTo(view.occupancies().get(2).entryTime());
    }

    @Test
    void approve_rejects_gap_between_legs() {
        sections("S1", "S2");
        PathRequest broken = new PathRequest("R1", List.of(
                leg("S1", T0, T0.plusSeconds(600), null),
                // 未安排停靠车站，但下一区间进入时间晚于上一区间离开时间，时间轴留了空档
                leg("S2", T0.plusSeconds(660), T0.plusSeconds(1200), null)));

        assertThatThrownBy(() -> pathService.approve(broken))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.VALIDATION);
    }

    @Test
    void approve_allows_pass_through_when_sections_meet_exactly() {
        sections("S1", "S2");
        PathRequest passThrough = new PathRequest("R1", List.of(
                leg("S1", T0, T0.plusSeconds(600), null),
                leg("S2", T0.plusSeconds(600), T0.plusSeconds(1200), null)));
        ReservationView view = pathService.approve(passThrough);
        // 不停靠：只有两个区间占用，时间首尾相接。
        assertThat(view.occupancies()).extracting(o -> o.resourceType() + ":" + o.resourceCode())
                .containsExactly("SECTION:S1", "SECTION:S2");
    }

    @Test
    void approve_rejects_non_positive_dwell_when_stopping() {
        sections("S1", "S2");
        stations("ST-A");
        PathRequest broken = new PathRequest("R1", List.of(
                leg("S1", T0, T0.plusSeconds(600), "ST-A"),
                // 停靠时长为 0：下一区间进入时间等于本站离开时间
                leg("S2", T0.plusSeconds(600), T0.plusSeconds(1200), null)));
        assertThatThrownBy(() -> pathService.approve(broken))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.VALIDATION);
    }

    @Test
    void approve_rejects_exit_before_entry() {
        sections("S1");
        PathRequest broken = single("R1", "S1", T0.plusSeconds(60), T0);
        assertThatThrownBy(() -> pathService.approve(broken))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.VALIDATION);
    }

    @Test
    void approve_rejects_unknown_section() {
        // 不注册任何资源
        PathRequest request = single("R1", "GHOST", T0, T0.plusSeconds(600));
        assertThatThrownBy(() -> pathService.approve(request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.RESOURCE_NOT_FOUND);
        // 失败后不得留下径路。
        assertThat(reservationRepository.count()).isZero();
        assertThat(legRepository.count()).isZero();
    }

    @Test
    void approve_rejects_unknown_station_and_leaves_no_partial_path() {
        sections("S1", "S2");
        // ST-A 未注册：S1 校验通过后 ST-A 失败，整条径路必须回滚。
        PathRequest request = chain("R1", List.of("S1", "S2"), List.of("ST-A"), T0, 10, 5);
        assertThatThrownBy(() -> pathService.approve(request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.RESOURCE_NOT_FOUND);
        assertThat(reservationRepository.count()).isZero();
        assertThat(legRepository.count()).isZero();
    }

    // ---------- 容量与追踪间隔 ----------

    @Test
    void approve_rejects_section_over_capacity_within_overlapping_window() {
        resourceService.registerSection(TestFixtures.section("S1", 1, 0));
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(600)));

        // 时间重叠，能力为 1。
        assertThatThrownBy(() ->
                pathService.approve(single("R2", "S1", T0.plusSeconds(300), T0.plusSeconds(900))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getType()).isEqualTo(ErrorType.CAPACITY_EXCEEDED);
                    assertThat(ex.getConflicts()).hasSize(1);
                    assertThat(ex.getConflicts().get(0).conflictingRunNos()).containsExactly("R1");
                });
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void approve_allows_two_trains_when_within_capacity() {
        resourceService.registerSection(TestFixtures.section("S1", 2, 0));
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(600)));
        ReservationView second = pathService.approve(
                single("R2", "S1", T0.plusSeconds(300), T0.plusSeconds(900)));
        assertThat(second.status()).isEqualTo("ACTIVE");
        assertThat(reservationRepository.count()).isEqualTo(2);
    }

    @Test
    void approve_allows_back_to_back_without_overlap() {
        resourceService.registerSection(TestFixtures.section("S1", 1, 0));
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(600)));
        // 左闭右开：后车恰在 R1 离开时刻进入，不重叠。
        ReservationView second = pathService.approve(
                single("R2", "S1", T0.plusSeconds(600), T0.plusSeconds(1200)));
        assertThat(second.status()).isEqualTo("ACTIVE");
    }

    @Test
    void approve_rejects_headway_violation_even_without_time_overlap() {
        resourceService.registerSection(TestFixtures.section("S1", 1, 600));
        // R1 占用很短后离开，R2 紧接着进入：时间不重叠，但进入间隔小于 600 秒。
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(120)));
        assertThatThrownBy(() ->
                pathService.approve(single("R2", "S1", T0.plusSeconds(120), T0.plusSeconds(240))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.CAPACITY_EXCEEDED);
    }

    @Test
    void approve_allows_when_headway_exactly_met() {
        resourceService.registerSection(TestFixtures.section("S1", 1, 600));
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(300)));
        ReservationView second = pathService.approve(
                single("R2", "S1", T0.plusSeconds(600), T0.plusSeconds(900)));
        assertThat(second.status()).isEqualTo("ACTIVE");
    }

    @Test
    void approve_rejects_headway_violation_when_capacity_allows_non_overlap() {
        // 能力为 2、追踪间隔 600 秒：两车时间窗首尾相接（不重叠），容量不超，
        // 但进入时刻仅相差 120 秒，必须因追踪间隔被拒。
        resourceService.registerSection(TestFixtures.section("S1", 2, 600));
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(120)));
        assertThatThrownBy(() ->
                pathService.approve(single("R2", "S1", T0.plusSeconds(120), T0.plusSeconds(240))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    assertThat(ex.getType()).isEqualTo(ErrorType.CAPACITY_EXCEEDED);
                    assertThat(ex.getConflicts()).anyMatch(c -> c.reason().contains("追踪间隔"));
                });
    }

    @Test
    void approve_rejects_station_over_track_count() {
        resourceService.registerSection(TestFixtures.section("S1", 5, 0));
        resourceService.registerSection(TestFixtures.section("S2", 5, 0));
        resourceService.registerStation(TestFixtures.station("ST-A", 1));

        pathService.approve(chain("R1", List.of("S1", "S2"), List.of("ST-A"), T0, 10, 20));
        // R2 在与 R1 停靠重叠的时间窗内停靠 ST-A。
        assertThatThrownBy(() -> pathService.approve(
                chain("R2", List.of("S1", "S2"), List.of("ST-A"),
                        T0.plusSeconds(5 * 60), 10, 20)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.CAPACITY_EXCEEDED);
    }

    // ---------- 幂等 ----------

    @Test
    void approve_is_idempotent_for_same_run_no_and_content() {
        sections("S1");
        PathRequest request = single("R1", "S1", T0, T0.plusSeconds(600));
        ReservationView first = pathService.approve(request);
        ReservationView replay = pathService.approve(request);

        assertThat(replay.requestHash()).isEqualTo(first.requestHash());
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(legRepository.count()).isEqualTo(1);
        // 只记录一次批准事件。
        assertThat(pathService.events("R1")).hasSize(1);
    }

    @Test
    void approve_same_run_no_different_content_conflicts() {
        sections("S1");
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(600)));
        assertThatThrownBy(() ->
                pathService.approve(single("R1", "S1", T0, T0.plusSeconds(900))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.CONFLICT);
    }

    // ---------- 改线 ----------

    @Test
    void reroute_replaces_old_path_only_after_new_path_fully_acquired() {
        sections("S1", "S2", "S3");
        stations("ST-A", "ST-B");
        pathService.approve(chain("R1", List.of("S1", "S2"), List.of("ST-A"), T0, 10, 5));

        // 改走 S1 -> S3（经过 ST-B）。
        List<LegRequest> newPath = List.of(
                leg("S1", T0, T0.plusSeconds(10 * 60), "ST-B"),
                leg("S3", T0.plusSeconds(15 * 60), T0.plusSeconds(25 * 60), null));
        ReservationView rerouted = pathService.reroute("R1", newPath);

        assertThat(rerouted.occupancies()).extracting(o -> o.resourceType() + ":" + o.resourceCode())
                .containsExactly("SECTION:S1", "STATION:ST-B", "SECTION:S3");
        // 数据库中旧占用段已被整体替换，无残留。
        assertThat(pathService.get("R1").occupancies()).hasSize(3);
        assertThat(legRepository.findAll())
                .noneMatch(l -> "S2".equals(l.getResourceCode()) || "ST-A".equals(l.getResourceCode()));
        assertThat(pathService.events("R1")).extracting(EventViewType.INSTANCE)
                .containsExactly("APPROVED", "REROUTED");
    }

    @Test
    void reroute_failure_keeps_original_plan_intact() {
        resourceService.registerSection(TestFixtures.section("S1", 1, 0));
        resourceService.registerSection(TestFixtures.section("S2", 1, 0));
        resourceService.registerSection(TestFixtures.section("S3", 1, 0));
        resourceService.registerStation(TestFixtures.station("ST-A", 1));
        resourceService.registerStation(TestFixtures.station("ST-B", 1));
        pathService.approve(chain("R1", List.of("S1", "S2"), List.of("ST-A"), T0, 10, 5));
        // R2 占用 S3，使 R1 想改入 S3 的新径路不可行。
        pathService.approve(single("R2", "S3",
                T0.plusSeconds(15 * 60), T0.plusSeconds(30 * 60)));

        List<LegRequest> newPath = List.of(
                leg("S1", T0, T0.plusSeconds(10 * 60), "ST-B"),
                leg("S3", T0.plusSeconds(15 * 60), T0.plusSeconds(25 * 60), null));
        assertThatThrownBy(() -> pathService.reroute("R1", newPath))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.CAPACITY_EXCEEDED);

        // 原计划完全不变。
        ReservationView current = pathService.get("R1");
        assertThat(current.occupancies()).extracting(o -> o.resourceType() + ":" + o.resourceCode())
                .containsExactly("SECTION:S1", "STATION:ST-A", "SECTION:S2");
        assertThat(pathService.events("R1")).hasSize(1);
    }

    @Test
    void reroute_unknown_run_is_resource_error() {
        sections("S1");
        assertThatThrownBy(() ->
                pathService.reroute("NOPE", List.of(leg("S1", T0, T0.plusSeconds(600), null))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getType())
                .isEqualTo(ErrorType.RESOURCE_NOT_FOUND);
    }

    // ---------- 取消与事件 ----------

    @Test
    void cancel_releases_capacity_and_is_idempotent() {
        sections("S1");
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(600)));

        ReservationView cancelled = pathService.cancel("R1");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        // 取消后同一时间窗可被新申请使用。
        pathService.approve(single("R2", "S1", T0.plusSeconds(100), T0.plusSeconds(300)));
        // 再次取消不产生重复事件、不报错。
        pathService.cancel("R1");
        assertThat(pathService.events("R1")).extracting(EventViewType.INSTANCE)
                .containsExactly("APPROVED", "CANCELLED");
    }

    @Test
    void cancelled_path_occupancy_is_still_queryable() {
        sections("S1");
        pathService.approve(single("R1", "S1", T0, T0.plusSeconds(600)));
        pathService.cancel("R1");
        ReservationView view = pathService.get("R1");
        assertThat(view.status()).isEqualTo("CANCELLED");
        assertThat(view.occupancies()).hasSize(1);
    }

    // ---------- 并发 ----------

    @Test
    void concurrent_requests_competing_same_section_admit_only_within_capacity() throws Exception {
        resourceService.registerSection(TestFixtures.section("S1", 2, 0));
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String runNo = "C" + i;
                // 全部在同一时间窗占用，能力 2，仅 2 个应成功。
                PathRequest req = single(runNo, "S1", T0, T0.plusSeconds(600));
                tasks.add(() -> {
                    try {
                        pathService.approve(req);
                        return "OK:" + runNo;
                    } catch (ApiException e) {
                        return "REJECT:" + runNo;
                    }
                });
            }
            List<Future<String>> futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get());
            }
            long ok = results.stream().filter(r -> r.startsWith("OK")).count();
            long rejected = results.stream().filter(r -> r.startsWith("REJECT")).count();
            assertThat(ok).as("同时占用不得超过通过能力 2").isEqualTo(2);
            assertThat(rejected).isEqualTo(threads - 2);
            assertThat(reservationRepository.count()).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrent_requests_with_headway_are_serialized_by_resource_lock() throws Exception {
        // 能力 1、追踪间隔 600 秒：并发提交进入时刻相差不足 600 秒时只能成功一个。
        resourceService.registerSection(TestFixtures.section("S1", 1, 600));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> a = () -> {
                pathService.approve(single("H1", "S1", T0, T0.plusSeconds(300)));
                return true;
            };
            Callable<Boolean> b = () -> {
                pathService.approve(single("H2", "S1", T0.plusSeconds(120), T0.plusSeconds(420)));
                return true;
            };
            Future<Boolean> fa = pool.submit(a);
            Future<Boolean> fb = pool.submit(b);
            int success = 0;
            try {
                fa.get(30, TimeUnit.SECONDS);
                success++;
            } catch (Exception ignored) {
            }
            try {
                fb.get(30, TimeUnit.SECONDS);
                success++;
            } catch (Exception ignored) {
            }
            assertThat(success).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrent_identical_submissions_for_same_run_no_all_return_original() throws Exception {
        sections("S1");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            // 相同运行号、相同内容并发提交：每个调用都应成功并返回同一径路，只落库一条。
            PathRequest req = single("DUP", "S1", T0, T0.plusSeconds(600));
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    ReservationView view = pathService.approve(req);
                    return view.externalRunNo() + ":" + view.requestHash().substring(0, 8);
                });
            }
            List<Future<String>> futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);
            for (Future<String> f : futures) {
                assertThat(f.get()).startsWith("DUP:");
            }
            assertThat(reservationRepository.count()).isEqualTo(1);
            assertThat(legRepository.count()).isEqualTo(1);
            assertThat(pathService.events("DUP")).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** 读取事件类型名称的小提取器，避免在断言里写完整 lambda 类型。 */
    private enum EventViewType implements java.util.function.Function<com.chris64233.railpath.api.EventView, String> {
        INSTANCE;

        @Override
        public String apply(com.chris64233.railpath.api.EventView event) {
            return event.eventType();
        }
    }
}
