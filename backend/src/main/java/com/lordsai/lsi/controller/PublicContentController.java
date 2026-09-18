package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.review.ReviewDtos.PublicReview;
import com.lordsai.lsi.dto.review.ReviewDtos.ReviewRequest;
import com.lordsai.lsi.dto.story.StoryDtos.PublicStory;
import com.lordsai.lsi.entity.SuccessStory;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.dto.slider.SliderDtos.PublicSlide;
import com.lordsai.lsi.entity.enums.StoryCategory;
import com.lordsai.lsi.service.FileStorageService;
import com.lordsai.lsi.service.HomepageSliderService;
import com.lordsai.lsi.service.SiteContentService;
import com.lordsai.lsi.service.ReviewService;
import com.lordsai.lsi.service.SuccessStoryService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import com.lordsai.lsi.util.VideoStreaming;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public")
public class PublicContentController {

    private final SiteContentService contentService;
    private final SuccessStoryService storyService;
    private final ReviewService reviewService;
    private final HomepageSliderService sliderService;
    private final FileStorageService storage;

    public PublicContentController(SiteContentService contentService, SuccessStoryService storyService,
                                   ReviewService reviewService, HomepageSliderService sliderService,
                                   FileStorageService storage) {
        this.contentService = contentService;
        this.storyService = storyService;
        this.reviewService = reviewService;
        this.sliderService = sliderService;
        this.storage = storage;
    }

    /**
     * Active Mutual Fund homepage slider images, in display order. Used only by the existing
     * Mutual Fund hero carousel on home.html; the Share Market homepage slider does not call this.
     */
    @GetMapping("/mutual-fund/slider-images")
    public ApiResponse<List<PublicSlide>> mutualFundSliderImages() {
        return ApiResponse.ok(sliderService.activeSlides());
    }

    /** Approved visitor reviews for one website. Pending / declined rows never leave the server. */
    @GetMapping("/reviews")
    public ApiResponse<List<PublicReview>> reviews(@RequestParam(required = false) SiteCode site) {
        return ApiResponse.ok(reviewService.approved(site));
    }

    /** Visitor review submission (multipart so an optional photo can be attached). Stored as PENDING. */
    @PostMapping(value = "/reviews", consumes = "multipart/form-data")
    public ApiResponse<PublicReview> submitReview(@Valid @ModelAttribute ReviewRequest body,
                                                  @RequestParam(value = "photo", required = false) MultipartFile photo,
                                                  HttpServletRequest req) {
        return ApiResponse.ok("Thank you for sharing your experience! Your review has been submitted successfully "
                + "and is awaiting approval by our team. It will be published after approval.",
                reviewService.submit(body, photo, RequestUtil.clientIp(req)));
    }

    @GetMapping("/content/{site}")
    public ApiResponse<Map<String, String>> content(@PathVariable SiteCode site) {
        return ApiResponse.ok(contentService.publicContent(site));
    }

    /** Published success stories only, optionally filtered by category (STUDENTS / TEACHERS / PARENTS). */
    @GetMapping("/stories")
    public ApiResponse<List<PublicStory>> stories(@RequestParam(required = false) StoryCategory category) {
        return ApiResponse.ok(storyService.listPublished(category));
    }

    /** Streams an uploaded, published story video. Unpublished stories are not found here. */
    @GetMapping("/stories/{id}/video")
    public ResponseEntity<ResourceRegion> storyVideo(@PathVariable Long id,
                                                     @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {
        SuccessStory story = storyService.requirePublishedWithVideo(id);
        return VideoStreaming.partial(storage.load(story.getVideoPath()), rangeHeader, "public, max-age=3600");
    }

    /** Serves admin-uploaded public images (course thumbnails, banners). Videos/PDFs are never served here. */
    @GetMapping("/images/{filename:[A-Za-z0-9._-]+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        Resource file = storage.load("images/" + filename);
        MediaType type = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(file);
    }
}
