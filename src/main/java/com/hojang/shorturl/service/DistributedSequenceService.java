package com.hojang.shorturl.service;

import com.hojang.shorturl.domain.GlobalSequence;
import com.hojang.shorturl.repository.SequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class DistributedSequenceService {
    private static final String SEQ_KEY = "short_url_seq";
    private static final long BATCH_SIZE = 1000; // 한 번에 가져올 범위 크기

    private final SequenceRepository sequenceRepository;
    private final TransactionTemplate transactionTemplate;

    private final AtomicLong currentId = new AtomicLong(0);
    private long maxId = 0;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        transactionTemplate.executeWithoutResult(status -> {
            if (!sequenceRepository.existsById(SEQ_KEY)) {
                sequenceRepository.save(new GlobalSequence(SEQ_KEY, 0L));
            }
            allocateNewRange();
        });
    }

    public synchronized long nextId() {
        long next = currentId.incrementAndGet();
        if (next > maxId) {
            allocateNewRange(); // 범위 다 쓰면 DB 접속
            next = currentId.incrementAndGet();
        }
        return next;
    }

    protected void allocateNewRange() {
        transactionTemplate.executeWithoutResult(status -> {
            GlobalSequence seq = sequenceRepository.findByIdWithLock(SEQ_KEY).orElseThrow();
            long prevMax = seq.getMaxId();
            long newMax = prevMax + BATCH_SIZE;
            seq.setMaxId(newMax);

            // 메모리 값 갱신 (트랜잭션 안에서 수행)
            this.currentId.set(prevMax);
            this.maxId = newMax;
        });
    }
}