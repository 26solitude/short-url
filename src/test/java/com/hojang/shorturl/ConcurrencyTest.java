package com.hojang.shorturl;

import com.hojang.shorturl.service.DistributedSequenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ConcurrencyTest {

    @Autowired
    private DistributedSequenceService sequenceService;

    @Test
    @DisplayName("100개의 스레드가 동시에 ID를 요청해도 중복이 없어야 한다")
    void concurrencyTest() throws InterruptedException {
        int numberOfThreads = 100;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        Set<Long> usedIds = ConcurrentHashMap.newKeySet();

        try (ExecutorService service = Executors.newFixedThreadPool(numberOfThreads)) {

            for (int i = 0; i < numberOfThreads; i++) {
                service.execute(() -> {
                    try {
                        long id = sequenceService.nextId();
                        usedIds.add(id);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        // 검증: 100번 요청했으면 Set 사이즈도 정확히 100이어야 함 (중복이 있었다면 100보다 작음)
        assertEquals(numberOfThreads, usedIds.size());

        System.out.println("성공! 생성된 유니크 ID 개수: " + usedIds.size());
    }
}