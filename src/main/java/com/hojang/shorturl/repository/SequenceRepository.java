package com.hojang.shorturl.repository;

import com.hojang.shorturl.domain.GlobalSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SequenceRepository extends JpaRepository<GlobalSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) // 다른 서버가 동시에 건드리지 못하게 락
    @Query("SELECT s FROM GlobalSequence s WHERE s.id = :id")
    Optional<GlobalSequence> findByIdWithLock(@Param("id") String id);
}