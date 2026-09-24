package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Integer> {

    /** Row-locked read so two concurrent purchases cannot receive the same invoice number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvoiceSequence s where s.year = :year")
    Optional<InvoiceSequence> findForUpdate(@Param("year") int year);
}
