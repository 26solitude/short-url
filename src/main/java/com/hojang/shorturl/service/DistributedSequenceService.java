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
    private static final long BATCH_SIZE = 1000;

    private static final long INITIAL_VALUE = 56_800_235_584L;

    private final SequenceRepository sequenceRepository;
    private final TransactionTemplate transactionTemplate;

    private final AtomicLong currentId = new AtomicLong(0);
    private long maxId = 0;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        transactionTemplate.executeWithoutResult(status -> {
            if (!sequenceRepository.existsById(SEQ_KEY)) {
                sequenceRepository.save(new GlobalSequence(SEQ_KEY, INITIAL_VALUE));
            }
            allocateNewRange();
        });
    }

    public synchronized long nextId() {
        long next = currentId.incrementAndGet();
        if (next > maxId) {
            allocateNewRange();
            next = currentId.incrementAndGet();
        }
        return next;
    }

    protected void allocateNewRange() {
        transactionTemplate.executeWithoutResult(status -> {
            GlobalSequence seq = sequenceRepository.findByIdWithLock(SEQ_KEY)
                    .orElseThrow(() -> new IllegalStateException("Sequence not found"));
            long prevMax = seq.getMaxId();
            long newMax = prevMax + BATCH_SIZE;
            seq.setMaxId(newMax);
            this.currentId.set(prevMax);
            this.maxId = newMax;
        });
    }
}