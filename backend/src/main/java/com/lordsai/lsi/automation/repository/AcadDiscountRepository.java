package com.lordsai.lsi.automation.repository;

import com.lordsai.lsi.automation.entity.AcadDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcadDiscountRepository extends JpaRepository<AcadDiscount, Long> {
    List<AcadDiscount> findByStudentIdOrderByInstallmentNoAsc(Long studentId);
    Optional<AcadDiscount> findByStudentIdAndInstallmentNo(Long studentId, int installmentNo);
}
