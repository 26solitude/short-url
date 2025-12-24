package com.hojang.shorturl.service;


import com.hojang.shorturl.domain.GlobalSequence;
import com.hojang.shorturl.repository.SequenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DistributedSequenceServiceTest {

    // DistributedSequenceService에 정의된 상수 (테스트 검증용)
    private static final String SEQ_KEY = "short_url_seq";
    private static final long BATCH_SIZE = 1000L;
    private static final long INITIAL_VALUE = 56_800_235_584L;
    @Autowired
    private DistributedSequenceService sequenceService;
    @Autowired
    private SequenceRepository sequenceRepository;

    @Test
    @DisplayName("1. 초기화 검증: 애플리케이션 시작 시 DB에 초기값이 설정되어 있어야 한다")
    void initTest() {
        // given & when (앱 실행 시 자동 init)
        GlobalSequence globalSequence = sequenceRepository.findById(SEQ_KEY).orElseThrow();

        // then
        // 초기에는 currentId가 maxId(초기값)와 같게 설정됨
        // 주의: 이미 앱이 떴을 때 allocateNewRange()가 한 번 돌았을 수 있으므로
        // 최소한 INITIAL_VALUE보다는 크거나 같아야 함을 검증
        assertThat(globalSequence.getMaxId()).isGreaterThanOrEqualTo(INITIAL_VALUE);
    }

    @Test
    @DisplayName("2. 범위 할당 검증: 배치 사이즈(1000)를 초과하면 DB의 MaxId가 증가해야 한다")
    void rangeAllocationTest() {
        // given
        // 현재 DB 상태 확인
        GlobalSequence beforeSeq = sequenceRepository.findById(SEQ_KEY).orElseThrow();
        Long beforeMaxId = beforeSeq.getMaxId();

        // when
        // 배치 사이즈(1000) + 10번 만큼 ID를 소모하여 강제로 DB 갱신을 유발
        int loopCount = (int) BATCH_SIZE + 10;
        for (int i = 0; i < loopCount; i++) {
            sequenceService.nextId();
        }

        // then
        GlobalSequence afterSeq = sequenceRepository.findById(SEQ_KEY).orElseThrow();
        Long afterMaxId = afterSeq.getMaxId();

        System.out.println("Before MaxId: " + beforeMaxId);
        System.out.println("After MaxId: " + afterMaxId);

        // DB의 maxId가 최소한 한 번 이상 증가했어야 함
        assertThat(afterMaxId).isGreaterThan(beforeMaxId);
    }

    @Test
    @DisplayName("3. 동시성 통합 테스트: 스레드가 배치를 넘겨서 요청해도 중복이 없고 DB 락이 작동해야 한다")
    void concurrencyIntegrationTest() throws InterruptedException {
        // given
        int numberOfThreads = 20;
        int requestsPerThread = 100;
        int totalRequests = numberOfThreads * requestsPerThread;


        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        Set<Long> generatedIds = ConcurrentHashMap.newKeySet();

        // try-with-resources: 블록이 끝나면 executorService.close()가 자동 호출됨
        try (ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads)) {
            // when
            for (int i = 0; i < numberOfThreads; i++) {
                executorService.execute(() -> {
                    try {
                        for (int j = 0; j < requestsPerThread; j++) {
                            long id = sequenceService.nextId();
                            generatedIds.add(id);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();

        // then
        assertThat(generatedIds.size()).isEqualTo(totalRequests);
        System.out.println("Generated " + generatedIds.size() + " unique IDs.");
    }
}