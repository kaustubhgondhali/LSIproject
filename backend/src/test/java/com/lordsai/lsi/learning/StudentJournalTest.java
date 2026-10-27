package com.lordsai.lsi.learning;

import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Student General > Student Journal: a student's own daily trading journal and its performance summary. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentJournalTest {

    @Autowired ApiClient api;
    @Autowired TestUsers users;

    private String student, other;

    @BeforeEach
    void setUp() throws Exception {
        users.student("journal@test.local");
        users.student("someone-else@test.local");
        student = api.login("journal@test.local", TestUsers.PASSWORD);
        other = api.login("someone-else@test.local", TestUsers.PASSWORD);
    }

    private static Map<String, Object> trade(String type, double entry, double exit, int qty, double charges) {
        Map<String, Object> body = new HashMap<>();
        body.put("tradeDate", "2026-09-19");
        body.put("segment", "Equity");
        body.put("symbol", "reliance");
        body.put("tradeType", type);
        body.put("entryPrice", entry);
        body.put("exitPrice", exit);
        body.put("quantity", qty);
        body.put("charges", charges);
        body.put("strategy", "Price Action");
        body.put("tradeReason", "Breakout above resistance");
        body.put("outcomeReason", "Followed the plan");
        return body;
    }

    @Test
    void journalComputesPnlDerivesResultAndSummarises() throws Exception {
        // BUY 10 @ 2500 -> 2550, charges 40: gross +500, net +460 -> PROFIT (derived).
        long buyId = api.data(api.post(student, "/api/student/journal", trade("BUY", 2500, 2550, 10, 40)))
                .path("id").asLong();
        api.get(student, "/api/student/journal").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.data.content[0].segment").value("Equity"))
                .andExpect(jsonPath("$.data.content[0].grossPnl").value(500.0))
                .andExpect(jsonPath("$.data.content[0].netPnl").value(460.0))
                .andExpect(jsonPath("$.data.content[0].result").value("PROFIT"))
                .andExpect(jsonPath("$.data.content[0].tradeReason").value("Breakout above resistance"));

        // SELL 50 @ 120 -> 135, charges 60: gross -750, net -810 -> LOSS.
        api.post(student, "/api/student/journal", trade("SELL", 120, 135, 50, 60)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netPnl").value(-810.0))
                .andExpect(jsonPath("$.data.result").value("LOSS"));

        api.get(student, "/api/student/journal/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(2))
                .andExpect(jsonPath("$.data.winningTrades").value(1))
                .andExpect(jsonPath("$.data.losingTrades").value(1))
                .andExpect(jsonPath("$.data.winRatePercent").value(50))
                .andExpect(jsonPath("$.data.netPnl").value(-350.0))
                .andExpect(jsonPath("$.data.totalCharges").value(100.0));

        // An explicit result from the student is kept as entered.
        Map<String, Object> edited = trade("BUY", 2500, 2550, 10, 40);
        edited.put("result", "BREAKEVEN");
        api.put(student, "/api/student/journal/" + buyId, edited).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("BREAKEVEN"));
    }

    @Test
    void journalIsPrivateToItsStudent() throws Exception {
        long id = api.data(api.post(student, "/api/student/journal", trade("BUY", 100, 110, 1, 0))).path("id").asLong();

        api.get(other, "/api/student/journal").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
        api.get(other, "/api/student/journal/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrades").value(0));
        api.put(other, "/api/student/journal/" + id, trade("SELL", 1, 1, 1, 0)).andExpect(status().isNotFound());
        api.delete(other, "/api/student/journal/" + id).andExpect(status().isNotFound());

        api.delete(student, "/api/student/journal/" + id).andExpect(status().isOk());
        api.get(student, "/api/student/journal/summary").andExpect(jsonPath("$.data.totalTrades").value(0));
    }

    @Test
    void invalidQuantityIsRejected() throws Exception {
        api.post(student, "/api/student/journal", trade("BUY", 100, 110, 0, 0)).andExpect(status().isBadRequest());
    }

    @Test
    void typeOfTradingAndTypedStrategyAreSavedAndChargesAreOptional() throws Exception {
        // The Add a trade form no longer sends charges: net P&L is then the gross P&L.
        Map<String, Object> body = trade("BUY", 200, 230, 5, 0);
        body.remove("charges");
        body.put("tradingType", "MUTUAL_FUNDS");
        body.put("strategy", "My own 20-EMA pullback setup");
        long id = api.data(api.post(student, "/api/student/journal", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tradingType").value("MUTUAL_FUNDS"))
                .andExpect(jsonPath("$.data.strategy").value("My own 20-EMA pullback setup"))
                .andExpect(jsonPath("$.data.grossPnl").value(150.0))
                .andExpect(jsonPath("$.data.netPnl").value(150.0))
                .andExpect(jsonPath("$.data.result").value("PROFIT"))).path("id").asLong();

        for (String type : new String[]{"EQUITIES", "ETFS", "OPTIONS", "FUTURES", "COMMODITIES"}) {
            body.put("tradingType", type);
            api.put(student, "/api/student/journal/" + id, body).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tradingType").value(type));
        }

        // Only the six listed types are accepted.
        body.put("tradingType", "CRYPTO");
        api.put(student, "/api/student/journal/" + id, body).andExpect(status().isBadRequest());

        // Entries saved before the field existed (no type) still work.
        Map<String, Object> legacy = trade("BUY", 100, 110, 1, 0);
        api.post(student, "/api/student/journal", legacy).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tradingType").isEmpty());
    }
}
