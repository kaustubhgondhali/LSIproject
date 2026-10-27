package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Payment;
import com.lordsai.lsi.entity.enums.PaymentMode;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findByOrderRef(String orderRef);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    long countByStatus(PaymentStatus status);

    long countByStatusAndPaymentMode(PaymentStatus status, PaymentMode paymentMode);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    List<Payment> findTop10ByOrderByCreatedAtDesc();
}
