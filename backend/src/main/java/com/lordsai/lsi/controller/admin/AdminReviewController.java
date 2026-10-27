package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.review.ReviewDtos.AdminReview;
import com.lordsai.lsi.dto.review.ReviewDtos.AdminReviewRequest;
import com.lordsai.lsi.dto.review.ReviewDtos.ReviewCounts;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.ReviewStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.ReviewService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Review moderation (ADMIN only — enforced by SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    public AdminReviewController(ReviewService reviewService, UserService userService) {
        this.reviewService = reviewService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<Page<AdminReview>> list(@RequestParam(required = false) ReviewStatus status,
                                               @RequestParam(required = false) SiteCode site,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.search(status, site, PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/counts")
    public ApiResponse<ReviewCounts> counts() {
        return ApiResponse.ok(reviewService.counts());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReview> get(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.get(id));
    }

    /** Admin adds a testimonial directly (visible immediately when status is APPROVED, the default). */
    @PostMapping
    public ApiResponse<AdminReview> create(@Valid @RequestBody AdminReviewRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Testimonial added.", reviewService.createByAdmin(body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminReview> update(@PathVariable Long id, @Valid @RequestBody AdminReviewRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Testimonial updated.", reviewService.updateByAdmin(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping(value = "/{id}/photo", consumes = "multipart/form-data")
    public ApiResponse<AdminReview> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file, HttpServletRequest req) {
        return ApiResponse.ok("Photo updated.", reviewService.uploadPhoto(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/{id}/photo")
    public ApiResponse<AdminReview> removePhoto(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Photo removed.", reviewService.removePhoto(id, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<AdminReview> approve(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Review approved. It is now visible on the website.",
                reviewService.approve(id, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/{id}/decline")
    public ApiResponse<AdminReview> decline(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Review declined. It stays hidden from the website.",
                reviewService.decline(id, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        reviewService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Review deleted.");
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
