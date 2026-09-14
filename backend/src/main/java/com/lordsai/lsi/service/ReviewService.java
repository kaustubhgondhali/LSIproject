package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.review.ReviewDtos.AdminReview;
import com.lordsai.lsi.dto.review.ReviewDtos.AdminReviewRequest;
import com.lordsai.lsi.dto.review.ReviewDtos.PublicReview;
import com.lordsai.lsi.dto.review.ReviewDtos.ReviewCounts;
import com.lordsai.lsi.dto.review.ReviewDtos.ReviewRequest;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.entity.Review;
import com.lordsai.lsi.entity.Site;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.ReviewStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.ReviewRepository;
import com.lordsai.lsi.repository.SiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Visitor testimonials with admin moderation. Public reads are restricted to APPROVED rows
 * here, in the repository query — never by the frontend.
 */
@Service
public class ReviewService {

    /** A visitor may have one pending review per website, and at most 3 submissions per day. */
    private static final int MAX_SUBMISSIONS_PER_DAY = 3;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");

    private final ReviewRepository repository;
    private final SiteRepository siteRepository;
    private final FileStorageService storage;
    private final EmailService emailService;
    private final AuditService auditService;

    public ReviewService(ReviewRepository repository, SiteRepository siteRepository, FileStorageService storage,
                         EmailService emailService, AuditService auditService) {
        this.repository = repository;
        this.siteRepository = siteRepository;
        this.storage = storage;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    // ---- Canonical site resolution --------------------------------------------------------

    public static SiteCode canonical(SiteCode site) {
        if (site == SiteCode.SHARE_MARKET) {
            return SiteCode.ACADEMY;
        }
        return site == null ? SiteCode.ACADEMY : site;
    }

    // ---- Public ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicReview> approved(SiteCode site) {
        return repository.findApproved(canonical(site)).stream().map(this::toPublic).toList();
    }

    @Transactional
    public PublicReview submit(ReviewRequest req, MultipartFile photo, String ip) {
        SiteCode targetSite = canonical(req.site());
        Site site = siteRepository.findBySiteCode(targetSite)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown website."));
        String email = AuthService.normalizeEmail(req.email());
        String text = clean(req.reviewText());
        if (text.length() < com.lordsai.lsi.dto.review.ReviewDtos.MIN_REVIEW_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please write a little more about your experience (at least "
                    + com.lordsai.lsi.dto.review.ReviewDtos.MIN_REVIEW_LENGTH + " characters).");
        }
        if (URL.matcher(text).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Links are not allowed in reviews.");
        }
        if (repository.countPendingByEmail(email, targetSite) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You already have a review awaiting approval. Thank you for your patience!");
        }
        if (repository.countRecentByEmail(email, Instant.now().minus(Duration.ofDays(1))) >= MAX_SUBMISSIONS_PER_DAY) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "You have submitted several reviews recently. Please try again tomorrow.");
        }

        Review r = new Review();
        r.setSite(site);
        r.setFullName(clean(req.fullName()));
        r.setEmail(email);
        r.setCourse(blankToNull(clean(req.course())));
        r.setRating(req.rating());
        r.setReviewText(text);
        r.setStatus(ReviewStatus.PENDING);
        r.setSubmittedIp(ip);
        if (photo != null && !photo.isEmpty()) {
            r.setProfileImagePath(storage.store(photo, FileStorageService.Kind.IMAGE));
        }
        repository.save(r);
        auditService.record(null, "REVIEW_SUBMITTED", "Review", r.getId(),
                req.site() + " / " + r.getFullName() + " (" + r.getRating() + "★)", ip);
        emailService.sendReviewReceived(email, r.getFullName());
        return toPublic(r);
    }

    // ---- Admin -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AdminReview> search(ReviewStatus status, SiteCode site, Pageable pageable) {
        SiteCode targetSite = site == null ? null : canonical(site);
        return repository.search(status, targetSite, pageable).map(this::toAdmin);
    }

    @Transactional(readOnly = true)
    public AdminReview get(Long id) {
        return toAdmin(require(id));
    }

    @Transactional(readOnly = true)
    public ReviewCounts counts() {
        return new ReviewCounts(repository.countByStatus(ReviewStatus.PENDING),
                repository.countByStatus(ReviewStatus.APPROVED), repository.countByStatus(ReviewStatus.DECLINED));
    }

    @Transactional
    public AdminReview approve(Long id, User actor, String ip) {
        Review r = require(id);
        if (r.getStatus() == ReviewStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "This review is already approved.");
        }
        r.setStatus(ReviewStatus.APPROVED);
        r.setApprovedAt(Instant.now());
        r.setApprovedBy(actor);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(actor);
        repository.save(r);
        auditService.record(actor, "REVIEW_APPROVED", "Review", id, r.getFullName() + " (" + r.getRating() + "★)", ip);
        if (r.getEmail() != null && !r.getEmail().isBlank()) {
            emailService.sendReviewApproved(r.getEmail(), r.getFullName());
        }
        return toAdmin(r);
    }

    @Transactional
    public AdminReview decline(Long id, User actor, String ip) {
        Review r = require(id);
        if (r.getStatus() == ReviewStatus.DECLINED) {
            throw new ApiException(HttpStatus.CONFLICT, "This review is already declined.");
        }
        r.setStatus(ReviewStatus.DECLINED);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(actor);
        repository.save(r);
        auditService.record(actor, "REVIEW_DECLINED", "Review", id, r.getFullName(), ip);
        return toAdmin(r);
    }

    // ---- Admin-authored testimonials (same table, same public visibility rules) -------------

    @Transactional
    public AdminReview createByAdmin(AdminReviewRequest req, User actor, String ip) {
        SiteCode targetSite = canonical(req.site());
        Site site = siteRepository.findBySiteCode(targetSite)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown website."));
        Review r = new Review();
        r.setSite(site);
        applyAdminFields(r, req);
        r.setStatus(ReviewStatus.PENDING);
        applyStatus(r, req.status() == null ? ReviewStatus.APPROVED : req.status(), actor);
        repository.save(r);
        auditService.record(actor, "REVIEW_CREATED_BY_ADMIN", "Review", r.getId(),
                targetSite + " / " + r.getFullName() + " (" + r.getRating() + "★) " + r.getStatus(), ip);
        return toAdmin(r);
    }

    @Transactional
    public AdminReview updateByAdmin(Long id, AdminReviewRequest req, User actor, String ip) {
        Review r = require(id);
        SiteCode targetSite = canonical(req.site());
        if (r.getSite().getSiteCode() != targetSite) {
            r.setSite(siteRepository.findBySiteCode(targetSite)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown website.")));
        }
        applyAdminFields(r, req);
        if (req.status() != null && req.status() != r.getStatus()) {
            applyStatus(r, req.status(), actor);
        }
        repository.save(r);
        auditService.record(actor, "REVIEW_UPDATED", "Review", id, r.getFullName() + " (" + r.getRating() + "★) " + r.getStatus(), ip);
        return toAdmin(r);
    }

    @Transactional
    public AdminReview uploadPhoto(Long id, MultipartFile photo, User actor, String ip) {
        if (photo == null || photo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose an image to upload.");
        }
        Review r = require(id);
        String previous = r.getProfileImagePath();
        r.setProfileImagePath(storage.store(photo, FileStorageService.Kind.IMAGE));
        repository.save(r);
        storage.deleteQuietly(previous);
        auditService.record(actor, "REVIEW_PHOTO_UPLOADED", "Review", id, r.getFullName(), ip);
        return toAdmin(r);
    }

    @Transactional
    public AdminReview removePhoto(Long id, User actor, String ip) {
        Review r = require(id);
        storage.deleteQuietly(r.getProfileImagePath());
        r.setProfileImagePath(null);
        repository.save(r);
        auditService.record(actor, "REVIEW_PHOTO_REMOVED", "Review", id, r.getFullName(), ip);
        return toAdmin(r);
    }

    private static void applyAdminFields(Review r, AdminReviewRequest req) {
        String text = clean(req.reviewText());
        if (text.length() < com.lordsai.lsi.dto.review.ReviewDtos.MIN_REVIEW_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The testimonial must be at least "
                    + com.lordsai.lsi.dto.review.ReviewDtos.MIN_REVIEW_LENGTH + " characters.");
        }
        r.setFullName(clean(req.fullName()));
        r.setEmail(blankToNull(req.email()) == null ? null : AuthService.normalizeEmail(req.email()));
        r.setCourse(blankToNull(clean(req.course())));
        r.setRating(req.rating());
        r.setReviewText(text);
    }

    /** Same bookkeeping as accept/decline so the admin list and public page stay consistent. */
    private static void applyStatus(Review r, ReviewStatus status, User actor) {
        r.setStatus(status);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(actor);
        if (status == ReviewStatus.APPROVED) {
            r.setApprovedAt(Instant.now());
            r.setApprovedBy(actor);
        }
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        Review r = require(id);
        storage.deleteQuietly(r.getProfileImagePath());
        repository.delete(r);
        auditService.record(actor, "REVIEW_DELETED", "Review", id, r.getFullName() + " / " + r.getEmail(), ip);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private Review require(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Review", id));
    }

    /** Server-side sanitisation: strip tags and control characters, collapse whitespace. */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        String s = HTML_TAG.matcher(value).replaceAll("");
        s = CONTROL_CHARS.matcher(s).replaceAll("");
        return s.replaceAll("[ \\t]{2,}", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private PublicReview toPublic(Review r) {
        return new PublicReview(r.getId(), r.getFullName(), r.getCourse(), r.getRating(), r.getReviewText(),
                r.getProfileImagePath(), r.getApprovedAt());
    }

    private AdminReview toAdmin(Review r) {
        return new AdminReview(r.getId(), r.getSite().getSiteCode(), r.getFullName(), r.getEmail(), r.getCourse(),
                r.getRating(), r.getReviewText(), r.getProfileImagePath(), r.getStatus(), r.getSubmittedIp(),
                r.getCreatedAt(), r.getApprovedAt(),
                r.getApprovedBy() == null ? null : r.getApprovedBy().getFullName(),
                r.getDecidedAt(), r.getDecidedBy() == null ? null : r.getDecidedBy().getFullName());
    }
}
