package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.TradeJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeJournalRepository extends JpaRepository<TradeJournal, Long> {

    Page<TradeJournal> findByStudentIdOrderByTradeDateDescCreatedAtDesc(Long studentUserId, Pageable pageable);

    List<TradeJournal> findByStudentId(Long studentUserId);

    Optional<TradeJournal> findByIdAndStudentId(Long id, Long studentUserId);

    long countByStudentId(Long studentUserId);
}
