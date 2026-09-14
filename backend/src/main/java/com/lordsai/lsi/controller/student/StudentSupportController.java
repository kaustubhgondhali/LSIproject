package com.lordsai.lsi.controller.student;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtDetail;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtReplyRequest;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtRequest;
import com.lordsai.lsi.dto.support.SupportDtos.DoubtSummary;
import com.lordsai.lsi.dto.support.SupportDtos.TradeRequest;
import com.lordsai.lsi.dto.support.SupportDtos.TradeResponse;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.DoubtService;
import com.lordsai.lsi.service.TradeJournalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trade journal and doubt desk, scoped to the logged-in student. */
@RestController
@RequestMapping("/api/student")
public class StudentSupportController {

    private final TradeJournalService journalService;
    private final DoubtService doubtService;

    public StudentSupportController(TradeJournalService journalService, DoubtService doubtService) {
        this.journalService = journalService;
        this.doubtService = doubtService;
    }

    // ---- Trade journal ---------------------------------------------------------------------

    @GetMapping("/journal")
    public ApiResponse<Page<TradeResponse>> journal(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(journalService.myEntries(me(), PageRequest.of(page, Math.min(size, 100))));
    }

    @PostMapping("/journal")
    public ApiResponse<TradeResponse> addTrade(@Valid @RequestBody TradeRequest body) {
        return ApiResponse.ok("Trade entry saved.", journalService.create(me(), body));
    }

    @PutMapping("/journal/{id}")
    public ApiResponse<TradeResponse> updateTrade(@PathVariable Long id, @Valid @RequestBody TradeRequest body) {
        return ApiResponse.ok("Trade entry updated.", journalService.update(me(), id, body));
    }

    @DeleteMapping("/journal/{id}")
    public ApiResponse<Void> deleteTrade(@PathVariable Long id) {
        journalService.delete(me(), id);
        return ApiResponse.message("Trade entry deleted.");
    }

    // ---- Doubt desk ------------------------------------------------------------------------

    @GetMapping("/doubts")
    public ApiResponse<Page<DoubtSummary>> doubts(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(doubtService.mine(me(), PageRequest.of(page, Math.min(size, 100))));
    }

    @PostMapping("/doubts")
    public ApiResponse<DoubtSummary> askDoubt(@Valid @RequestBody DoubtRequest body) {
        return ApiResponse.ok("Your doubt has been submitted to the mentor desk.", doubtService.create(me(), body));
    }

    @GetMapping("/doubts/{id}")
    public ApiResponse<DoubtDetail> doubt(@PathVariable Long id) {
        return ApiResponse.ok(doubtService.mineDetail(me(), id));
    }

    @PostMapping("/doubts/{id}/replies")
    public ApiResponse<DoubtDetail> replyToDoubt(@PathVariable Long id, @Valid @RequestBody DoubtReplyRequest body) {
        return ApiResponse.ok(doubtService.reply(me(), id, body.message()));
    }

    private Long me() {
        return CurrentUser.require().id();
    }
}
