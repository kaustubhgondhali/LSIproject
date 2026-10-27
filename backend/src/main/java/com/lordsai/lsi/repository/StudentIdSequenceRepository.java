package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.StudentIdSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentIdSequenceRepository extends JpaRepository<StudentIdSequence, Integer> {

    /** Row-locked read so two concurrent purchases cannot receive the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudentIdSequence s where s.year = :year")
    Optional<StudentIdSequence> findForUpdate(@Param("year") int year);
}
