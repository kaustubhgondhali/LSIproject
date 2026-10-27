package com.lordsai.lsi.automation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian-system (lakh / crore) rupee amounts in words, e.g. "Rupees Ten Thousand Only". */
public final class AmountInWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {
    }

    public static String rupees(BigDecimal amount) {
        if (amount == null) {
            return "Rupees Zero Only";
        }
        BigDecimal abs = amount.abs().setScale(2, RoundingMode.HALF_UP);
        long rupees = abs.longValue();
        int paise = abs.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder sb = new StringBuilder("Rupees ");
        sb.append(rupees == 0 ? "Zero" : words(rupees));
        if (paise > 0) {
            sb.append(" and ").append(words(paise)).append(" Paise");
        }
        return sb.append(" Only").toString().replaceAll("\\s+", " ").trim();
    }

    static String words(long n) {
        if (n == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (n >= 10_000_000) {
            sb.append(words(n / 10_000_000)).append(" Crore ");
            n %= 10_000_000;
        }
        if (n >= 100_000) {
            sb.append(words(n / 100_000)).append(" Lakh ");
            n %= 100_000;
        }
        if (n >= 1_000) {
            sb.append(words(n / 1_000)).append(" Thousand ");
            n %= 1_000;
        }
        if (n >= 100) {
            sb.append(ONES[(int) (n / 100)]).append(" Hundred ");
            n %= 100;
        }
        if (n >= 20) {
            sb.append(TENS[(int) (n / 10)]);
            if (n % 10 != 0) {
                sb.append(' ').append(ONES[(int) (n % 10)]);
            }
        } else if (n > 0) {
            sb.append(ONES[(int) n]);
        }
        return sb.toString().trim();
    }
}
