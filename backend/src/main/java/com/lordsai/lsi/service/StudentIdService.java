package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.StudentIdSequence;
import com.lordsai.lsi.repository.StudentIdSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** Produces LSI-YYYY-NNNNN identifiers, guaranteed unique under concurrent purchases. */
@Service
public class StudentIdService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final StudentIdSequenceRepository sequenceRepository;

    public StudentIdService(StudentIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    /** Must be called inside the transaction that creates the student, so the lock is held until commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        int year = LocalDate.now(INDIA).getYear();
        StudentIdSequence seq = sequenceRepository.findForUpdate(year)
                .orElseGet(() -> sequenceRepository.saveAndFlush(new StudentIdSequence(year)));
        seq.setLastNumber(seq.getLastNumber() + 1);
        sequenceRepository.save(seq);
        return String.format("LSI-%d-%05d", year, seq.getLastNumber());
    }
}
