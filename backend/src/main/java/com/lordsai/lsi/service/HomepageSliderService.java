package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.slider.SliderDtos.AdminSlide;
import com.lordsai.lsi.dto.slider.SliderDtos.PublicSlide;
import com.lordsai.lsi.dto.slider.SliderDtos.SliderImageRequest;
import com.lordsai.lsi.entity.HomepageSliderImage;
import com.lordsai.lsi.entity.Site;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.HomepageSliderImageRepository;
import com.lordsai.lsi.repository.SiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin-managed homepage hero slider images. Today this only serves the Mutual Fund website's
 * existing carousel (home.html #mfCarouselTrack) — the Share Market homepage slider is untouched
 * and has no admin management. The slider itself keeps its existing markup/CSS/JS; only the
 * image list backing it becomes admin-controlled instead of hardcoded.
 */
@Service
public class HomepageSliderService {

    /** This feature is scoped to the Mutual Fund website only (see class javadoc). */
    private static final SiteCode TARGET_SITE = SiteCode.MUTUAL_FUND;

    private final HomepageSliderImageRepository repository;
    private final SiteRepository siteRepository;
    private final FileStorageService storage;
    private final AuditService auditService;

    public HomepageSliderService(HomepageSliderImageRepository repository, SiteRepository siteRepository,
                                 FileStorageService storage, AuditService auditService) {
        this.repository = repository;
        this.siteRepository = siteRepository;
        this.storage = storage;
        this.auditService = auditService;
    }

    // ---- Public ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicSlide> activeSlides() {
        return repository.findActiveForSite(TARGET_SITE).stream().map(this::toPublic).toList();
    }

    // ---- Admin -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminSlide> listAll() {
        return repository.findAllForSite(TARGET_SITE).stream().map(this::toAdmin).toList();
    }

    @Transactional
    public AdminSlide create(SliderImageRequest req, MultipartFile file, User actor, String ip) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose an image to upload.");
        }
        Site site = siteRepository.findBySiteCode(TARGET_SITE)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Mutual Fund website is not configured."));

        HomepageSliderImage img = new HomepageSliderImage();
        img.setSite(site);
        img.setImagePath(storage.store(file, FileStorageService.Kind.IMAGE));
        applyMetadata(img, req);
        img.setActive(true);
        img.setDisplayOrder(req.displayOrder() != null ? req.displayOrder() : (int) repository.findAllForSite(TARGET_SITE).size() + 1);
        img.setCreatedBy(actor);
        img.setUpdatedBy(actor);
        repository.save(img);
        auditService.record(actor, "SLIDER_IMAGE_CREATED", "HomepageSliderImage", img.getId(),
                "Mutual Fund homepage slide added" + (img.getTitle() != null ? " ('" + img.getTitle() + "')" : ""), ip);
        return toAdmin(img);
    }

    @Transactional
    public AdminSlide update(Long id, SliderImageRequest req, User actor, String ip) {
        HomepageSliderImage img = require(id);
        applyMetadata(img, req);
        if (req.displayOrder() != null) {
            img.setDisplayOrder(req.displayOrder());
        }
        img.setUpdatedBy(actor);
        repository.save(img);
        auditService.record(actor, "SLIDER_IMAGE_UPDATED", "HomepageSliderImage", id, describeOrder(img), ip);
        return toAdmin(img);
    }

    @Transactional
    public AdminSlide replaceImage(Long id, MultipartFile file, User actor, String ip) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a replacement image.");
        }
        HomepageSliderImage img = require(id);
        String previous = img.getImagePath();
        img.setImagePath(storage.store(file, FileStorageService.Kind.IMAGE));
        img.setUpdatedBy(actor);
        repository.save(img);
        if (previous != null && previous.startsWith("images/")) {
            storage.deleteQuietly(previous);
        }
        auditService.record(actor, "SLIDER_IMAGE_REPLACED", "HomepageSliderImage", id, describeOrder(img), ip);
        return toAdmin(img);
    }

    @Transactional
    public AdminSlide setActive(Long id, boolean active, User actor, String ip) {
        HomepageSliderImage img = require(id);
        img.setActive(active);
        img.setUpdatedBy(actor);
        repository.save(img);
        auditService.record(actor, active ? "SLIDER_IMAGE_ACTIVATED" : "SLIDER_IMAGE_DEACTIVATED",
                "HomepageSliderImage", id, describeOrder(img), ip);
        return toAdmin(img);
    }

    @Transactional
    public void reorder(List<Long> orderedIds, User actor, String ip) {
        int order = 1;
        for (Long id : orderedIds) {
            HomepageSliderImage img = require(id);
            img.setDisplayOrder(order++);
            repository.save(img);
        }
        auditService.record(actor, "SLIDER_IMAGE_REORDERED", "HomepageSliderImage", null,
                "Mutual Fund homepage slider reordered: " + orderedIds, ip);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        HomepageSliderImage img = require(id);
        if (img.getImagePath() != null && img.getImagePath().startsWith("images/")) {
            storage.deleteQuietly(img.getImagePath());
        }
        repository.delete(img);
        auditService.record(actor, "SLIDER_IMAGE_DELETED", "HomepageSliderImage", id, describeOrder(img), ip);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private HomepageSliderImage require(Long id) {
        HomepageSliderImage img = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Slider image", id));
        if (img.getSite().getSiteCode() != TARGET_SITE) {
            // Defence in depth: this table currently only ever holds Mutual Fund rows, but a
            // future site would never be reachable through these Mutual-Fund-only endpoints.
            throw ResourceNotFoundException.of("Slider image", id);
        }
        return img;
    }

    private static void applyMetadata(HomepageSliderImage img, SliderImageRequest req) {
        img.setTitle(blankToNull(req.title()));
        img.setSubtitle(blankToNull(req.subtitle()));
        img.setButtonText(blankToNull(req.buttonText()));
        img.setButtonLink(blankToNull(req.buttonLink()));
        img.setAltText(blankToNull(req.altText()));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String describeOrder(HomepageSliderImage img) {
        return "Slide #" + img.getDisplayOrder() + (img.getTitle() != null ? " ('" + img.getTitle() + "')" : "");
    }

    private PublicSlide toPublic(HomepageSliderImage img) {
        return new PublicSlide(img.getId(), img.getImagePath(), img.getTitle(), img.getSubtitle(),
                img.getButtonText(), img.getButtonLink(), img.getAltText());
    }

    private AdminSlide toAdmin(HomepageSliderImage img) {
        return new AdminSlide(img.getId(), img.getImagePath(), img.getTitle(), img.getSubtitle(),
                img.getButtonText(), img.getButtonLink(), img.getAltText(), img.getDisplayOrder(), img.isActive(),
                img.getCreatedBy() == null ? null : img.getCreatedBy().getFullName(),
                img.getUpdatedBy() == null ? null : img.getUpdatedBy().getFullName(),
                img.getCreatedAt(), img.getUpdatedAt());
    }
}
