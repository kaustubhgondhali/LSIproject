package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.InvoiceSequence;
import com.lordsai.lsi.repository.InvoiceSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Produces LSI-INV-YYYY-NNNNNN invoice numbers on the backend only. Unique, sequential per year
 * and guaranteed not to collide under concurrent purchases (row lock held until commit).
 */
@Service
public class InvoiceNumberService {

    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    public static final String PREFIX = "LSI-INV";

    private final InvoiceSequenceRepository sequenceRepository;

    public InvoiceNumberService(InvoiceSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    /** Must run inside the transaction that inserts the invoice, so the lock covers the insert. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        int year = LocalDate.now(INDIA).getYear();
        InvoiceSequence seq = sequenceRepository.findForUpdate(year)
                .orElseGet(() -> sequenceRepository.saveAndFlush(new InvoiceSequence(year)));
        seq.setLastNumber(seq.getLastNumber() + 1);
        sequenceRepository.save(seq);
        return String.format("%s-%d-%06d", PREFIX, year, seq.getLastNumber());
    }
}
