package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Doubt;
import com.lordsai.lsi.entity.enums.DoubtStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoubtRepository extends JpaRepository<Doubt, Long> {

    Page<Doubt> findByStudentIdOrderByCreatedAtDesc(Long studentUserId, Pageable pageable);

    Optional<Doubt> findByIdAndStudentId(Long id, Long studentUserId);

    Page<Doubt> findByStatusOrderByCreatedAtAsc(DoubtStatus status, Pageable pageable);

    long countByStatus(DoubtStatus status);
}
