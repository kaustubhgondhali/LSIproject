package com.lordsai.lsi.support;

import com.lordsai.lsi.automation.service.Aadhaar;

import java.util.concurrent.atomic.AtomicLong;

/** Sample Aadhaar numbers for tests: each one different, with a correct check digit, so they pass the form's checks. */
public final class TestAadhaar {

    private static final AtomicLong NEXT = new AtomicLong(23456789000L);

    private TestAadhaar() {
    }

    public static String next() {
        String eleven = Long.toString(NEXT.getAndIncrement());
        return eleven + Aadhaar.checkDigit(eleven);
    }
}
