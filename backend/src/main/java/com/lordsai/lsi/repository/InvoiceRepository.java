package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.enums.PaymentStatus;
import com.lordsai.lsi.entity.enums.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPaymentId(Long paymentId);

    Optional<Invoice> findByInvoiceNumberIgnoreCase(String invoiceNumber);

    List<Invoice> findByStudentIdOrderByPurchaseDateDesc(Long studentUserId);

    /** Ownership-scoped lookup: a student can only ever resolve their own invoice. */
    Optional<Invoice> findByIdAndStudentId(Long id, Long studentUserId);

    long countByProductType(ProductType productType);

    List<Invoice> findTop10ByOrderByPurchaseDateDesc();

    /** Admin / Automation Admin listing with the filters the screens expose. */
    @Query("""
            select i from Invoice i
            where (:productType is null or i.productType = :productType)
              and (:status is null or i.paymentStatus = :status)
              and (:studentUserId is null or i.student.id = :studentUserId)
              and (:courseId is null or i.course.id = :courseId)
              and (:ebookId is null or i.ebook.id = :ebookId)
              and (:from is null or i.purchaseDate >= :from)
              and (:to is null or i.purchaseDate < :to)
              and (:q is null
                   or lower(i.invoiceNumber) like lower(concat('%', :q, '%'))
                   or lower(i.studentName) like lower(concat('%', :q, '%'))
                   or lower(i.studentEmail) like lower(concat('%', :q, '%'))
                   or lower(coalesce(i.studentCode, '')) like lower(concat('%', :q, '%'))
                   or lower(i.productName) like lower(concat('%', :q, '%'))
                   or lower(coalesce(i.transactionId, '')) like lower(concat('%', :q, '%')))
            order by i.purchaseDate desc
            """)
    Page<Invoice> search(@Param("productType") ProductType productType,
                         @Param("status") PaymentStatus status,
                         @Param("studentUserId") Long studentUserId,
                         @Param("courseId") Long courseId,
                         @Param("ebookId") Long ebookId,
                         @Param("from") Instant from,
                         @Param("to") Instant to,
                         @Param("q") String q,
                         Pageable pageable);
}
