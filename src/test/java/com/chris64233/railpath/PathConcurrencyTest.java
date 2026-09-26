package com.chris64233.railpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.railpath.domain.Direction;
import com.chris64233.railpath.domain.Section;
import com.chris64233.railpath.repository.SectionRepository;
import com.chris64233.railpath.repository.StationRepository;
import com.chris64233.railpath.repository.TrainPathRepository;
import com.chris64233.railpath.service.ApiException;
import com.chris64233.railpath.service.ErrorCode;
import com.chris64233.railpath.service.PathService;
import com.chris64233.railpath.web.dto.LegRequest;
import com.chris64233.railpath.web.dto.PathApplyRequest;

/** 并发申请争抢相同区间时，只能允许符合容量限制的组合成功。 */
@SpringBootTest
class PathConcurrencyTest {

    @Autowired
    PathService service;
    @Autowired
    TrainPathRepository paths;
    @Autowired
    SectionRepository sections;
    @Autowired
    StationRepository stations;

    @BeforeEach
    void clean() {
        paths.deleteAll();
        sections.deleteAll();
        stations.deleteAll();
    }

    @Test
    void concurrentApplicationsRespectSectionCapacity() throws Exception {
        sections.save(new Section("S1", Direction.UP, 1, 0));

        // 两个线程同时争抢同一区间同一时间窗，容量 1：只能成功一个
        ConcurrentRun run = runConcurrently(2, "S1");
        assertThat(run.successes).isEqualTo(1);
        assertThat(run.capacityRejections).isEqualTo(1);
        assertThat(paths.findAll()).hasSize(1);
    }

    @Test
    void concurrentApplicationsFillCapacityExactly() throws Exception {
        sections.save(new Section("S1", Direction.UP, 2, 0));

        // 三个线程争抢容量为 2 的区间：恰好两个成功
        ConcurrentRun run = runConcurrently(3, "S1");
        assertThat(run.successes).isEqualTo(2);
        assertThat(run.capacityRejections).isEqualTo(1);
        assertThat(paths.findAll()).hasSize(2);
    }

    private ConcurrentRun runConcurrently(int threads, String sectionCode) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = java.util.stream.IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        start.await(5, TimeUnit.SECONDS);
                        try {
                            service.apply(new PathApplyRequest("T-C-" + i, List.of(
                                    new LegRequest(sectionCode,
                                            LocalDateTime.of(2030, 6, 1, 8, 0),
                                            LocalDateTime.of(2030, 6, 1, 8, 30))),
                                    List.of()));
                            return Outcome.SUCCESS;
                        } catch (ApiException e) {
                            return e.getCode() == ErrorCode.CAPACITY_EXCEEDED
                                    ? Outcome.CAPACITY_REJECTED : Outcome.OTHER_FAILURE;
                        }
                    }))
                    .toList();
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            int successes = 0;
            int capacityRejections = 0;
            for (Future<Outcome> f : futures) {
                Outcome outcome = f.get(30, TimeUnit.SECONDS);
                if (outcome == Outcome.SUCCESS) {
                    successes++;
                } else if (outcome == Outcome.CAPACITY_REJECTED) {
                    capacityRejections++;
                }
            }
            return new ConcurrentRun(successes, capacityRejections);
        } finally {
            pool.shutdownNow();
        }
    }

    private enum Outcome {
        SUCCESS, CAPACITY_REJECTED, OTHER_FAILURE
    }

    private record ConcurrentRun(int successes, int capacityRejections) {
    }
}
