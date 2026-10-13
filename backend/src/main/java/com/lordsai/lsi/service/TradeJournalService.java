package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.support.SupportDtos.TradeRequest;
import com.lordsai.lsi.dto.support.SupportDtos.TradeResponse;
import com.lordsai.lsi.dto.support.SupportDtos.TradeSummary;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.TradeJournal;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.TradeResult;
import com.lordsai.lsi.entity.enums.TradeReviewStatus;
import com.lordsai.lsi.entity.enums.TradeType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.StudentProfileRepository;
import com.lordsai.lsi.repository.TradeJournalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TradeJournalService {

    private final TradeJournalRepository journalRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserService userService;

    public TradeJournalService(TradeJournalRepository journalRepository,
                               StudentProfileRepository studentProfileRepository,
                               UserService userService) {
        this.journalRepository = journalRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userService = userService;
    }

    // ---- Student ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<TradeResponse> myEntries(Long studentUserId, Pageable pageable) {
        return journalRepository.findByStudentIdOrderByTradeDateDescCreatedAtDesc(studentUserId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TradeSummary summary(Long studentUserId) {
        List<TradeJournal> entries = journalRepository.findByStudentId(studentUserId);
        long wins = 0, losses = 0, even = 0;
        BigDecimal net = BigDecimal.ZERO, charges = BigDecimal.ZERO;
        for (TradeJournal t : entries) {
            if (t.getResult() == TradeResult.PROFIT) wins++;
            else if (t.getResult() == TradeResult.LOSS) losses++;
            else if (t.getResult() == TradeResult.BREAKEVEN) even++;
            BigDecimal pnl = netPnl(grossPnl(t), t.getCharges());
            if (pnl != null) net = net.add(pnl);
            if (t.getCharges() != null) charges = charges.add(t.getCharges());
        }
        long decided = wins + losses + even;
        int winRate = decided == 0 ? 0 : (int) Math.round(wins * 100.0 / decided);
        return new TradeSummary(entries.size(), wins, losses, even, winRate, net, charges);
    }

    @Transactional
    public TradeResponse create(Long studentUserId, TradeRequest req) {
        TradeJournal t = new TradeJournal();
        t.setStudent(userService.requireUser(studentUserId));
        apply(t, req);
        return toResponse(journalRepository.save(t));
    }

    @Transactional
    public TradeResponse update(Long studentUserId, Long id, TradeRequest req) {
        TradeJournal t = requireOwn(studentUserId, id);
        if (t.getReviewStatus() == TradeReviewStatus.REVIEWED) {
            throw new ApiException(HttpStatus.CONFLICT, "A reviewed entry can no longer be edited.");
        }
        apply(t, req);
        t.setReviewStatus(TradeReviewStatus.PENDING_REVIEW);
        return toResponse(journalRepository.save(t));
    }

    @Transactional
    public void delete(Long studentUserId, Long id) {
        TradeJournal t = requireOwn(studentUserId, id);
        if (t.getReviewStatus() == TradeReviewStatus.REVIEWED) {
            throw new ApiException(HttpStatus.CONFLICT, "A reviewed entry can no longer be deleted.");
        }
        journalRepository.delete(t);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private TradeJournal requireOwn(Long studentUserId, Long id) {
        return journalRepository.findByIdAndStudentId(id, studentUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Trade entry", id));
    }

    private static void apply(TradeJournal t, TradeRequest req) {
        t.setTradeDate(req.tradeDate());
        t.setSymbol(req.symbol().trim().toUpperCase());
        t.setTradeType(req.tradeType());
        t.setEntryPrice(req.entryPrice());
        t.setStopLoss(req.stopLoss());
        t.setTarget(req.target());
        t.setNotes(req.notes());
        t.setRiskReward(riskReward(req.entryPrice(), req.stopLoss(), req.target()));
        t.setSegment(blankToNull(req.segment()));
        t.setExitPrice(req.exitPrice());
        t.setQuantity(req.quantity());
        t.setCharges(req.charges());
        t.setStrategy(blankToNull(req.strategy()));
        t.setTradingType(req.tradingType());
        t.setTradeReason(blankToNull(req.tradeReason()));
        t.setOutcomeReason(blankToNull(req.outcomeReason()));
        // The student's own call wins; otherwise the result follows the computed net P&L.
        t.setResult(req.result() != null ? req.result() : resultOf(netPnl(grossPnl(t), t.getCharges())));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Long for BUY (and legacy non-directional types), short for SELL. */
    static BigDecimal grossPnl(TradeJournal t) {
        if (t.getEntryPrice() == null || t.getExitPrice() == null || t.getQuantity() == null) {
            return null;
        }
        BigDecimal perUnit = t.getTradeType() == TradeType.SELL
                ? t.getEntryPrice().subtract(t.getExitPrice())
                : t.getExitPrice().subtract(t.getEntryPrice());
        return perUnit.multiply(BigDecimal.valueOf(t.getQuantity())).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal netPnl(BigDecimal gross, BigDecimal charges) {
        if (gross == null) {
            return null;
        }
        return charges == null ? gross : gross.subtract(charges);
    }

    private static TradeResult resultOf(BigDecimal net) {
        if (net == null) {
            return null;
        }
        return net.signum() > 0 ? TradeResult.PROFIT : net.signum() < 0 ? TradeResult.LOSS : TradeResult.BREAKEVEN;
    }

    static String riskReward(BigDecimal entry, BigDecimal sl, BigDecimal target) {
        if (entry == null || sl == null || target == null) {
            return null;
        }
        BigDecimal risk = entry.subtract(sl).abs();
        BigDecimal reward = target.subtract(entry).abs();
        if (risk.signum() == 0) {
            return null;
        }
        return "1 : " + reward.divide(risk, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public TradeResponse toResponse(TradeJournal t) {
        User s = t.getStudent();
        String studentId = studentProfileRepository.findByUserId(s.getId()).map(StudentProfile::getStudentId).orElse(null);
        BigDecimal gross = grossPnl(t);
        return new TradeResponse(t.getId(), s.getId(), s.getFullName(), studentId, t.getTradeDate(), t.getSegment(),
                t.getSymbol(), t.getTradeType(), t.getEntryPrice(), t.getExitPrice(), t.getQuantity(), t.getCharges(),
                gross, netPnl(gross, t.getCharges()), t.getStrategy(), t.getResult(), t.getTradeReason(), t.getOutcomeReason(),
                t.getStopLoss(), t.getTarget(), t.getRiskReward(), t.getNotes(),
                t.getReviewStatus(), t.getMentorComment(),
                t.getReviewedBy() == null ? null : t.getReviewedBy().getFullName(), t.getReviewedAt(), t.getCreatedAt(),
                t.getTradingType());
    }
}
