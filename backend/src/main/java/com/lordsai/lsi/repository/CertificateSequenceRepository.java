package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.CertificateSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CertificateSequenceRepository extends JpaRepository<CertificateSequence, Integer> {

    /** Row-locked read so two certificates issued at the same moment cannot share a number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CertificateSequence s where s.year = :year")
    Optional<CertificateSequence> findForUpdate(@Param("year") int year);
}
