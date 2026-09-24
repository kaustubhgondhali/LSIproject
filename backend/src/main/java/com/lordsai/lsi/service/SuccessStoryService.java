package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.story.StoryDtos.AdminStory;
import com.lordsai.lsi.dto.story.StoryDtos.PublicStory;
import com.lordsai.lsi.dto.story.StoryDtos.StoryRequest;
import com.lordsai.lsi.entity.SuccessStory;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.StoryCategory;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.SuccessStoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin-managed Success Stories shown on the public stories page. Categories are content
 * labels (STUDENTS / TEACHERS / PARENTS); publishing is the only thing that makes a story
 * visible to visitors.
 */
@Service
public class SuccessStoryService {

    private final SuccessStoryRepository repository;
    private final FileStorageService storage;
    private final AuditService auditService;

    public SuccessStoryService(SuccessStoryRepository repository, FileStorageService storage, AuditService auditService) {
        this.repository = repository;
        this.storage = storage;
        this.auditService = auditService;
    }

    // ---- Public ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicStory> listPublished(StoryCategory category) {
        List<SuccessStory> stories = category == null
                ? repository.findByPublishedTrueOrderByDisplayOrderAscIdAsc()
                : repository.findByPublishedTrueAndCategoryOrderByDisplayOrderAscIdAsc(category);
        return stories.stream().map(this::toPublic).toList();
    }

    /** Only a published story with an uploaded file can be streamed; unpublished ones are simply "not found". */
    @Transactional(readOnly = true)
    public SuccessStory requirePublishedWithVideo(Long id) {
        SuccessStory s = repository.findByIdAndPublishedTrue(id).orElseThrow(() -> ResourceNotFoundException.of("Story", id));
        if (s.getVideoPath() == null || s.getVideoPath().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "This story has no uploaded video.");
        }
        return s;
    }

    // ---- Admin -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminStory> listAll() {
        return repository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public AdminStory get(Long id) {
        return toAdmin(require(id));
    }

    @Transactional
    public AdminStory create(StoryRequest req, User actor, String ip) {
        SuccessStory s = new SuccessStory();
        apply(s, req);
        s.setCreatedBy(actor);
        s.setUpdatedBy(actor);
        if (req.displayOrder() == null) {
            s.setDisplayOrder((int) repository.count() + 1);
        }
        repository.save(s);
        auditService.record(actor, "STORY_CREATED", "SuccessStory", s.getId(), s.getCategory() + " / " + s.getTitle(), ip);
        return toAdmin(s);
    }

    @Transactional
    public AdminStory createWithFiles(StoryCategory category, String title, String personName, String location,
                                      String description, MultipartFile coverImage, MultipartFile video,
                                      User actor, String ip) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Video name is required.");
        if (category == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Please select a category.");
        requireFile(coverImage, FileStorageService.Kind.IMAGE, "Cover image is required.");
        requireFile(video, FileStorageService.Kind.VIDEO, "Video file is required.");
        String coverPath = null;
        String videoPath = null;
        try {
            coverPath = storage.store(coverImage, FileStorageService.Kind.IMAGE);
            videoPath = storage.store(video, FileStorageService.Kind.VIDEO);
            SuccessStory s = new SuccessStory();
            s.setCategory(category);
            s.setTitle(cleanTitle);
            s.setPersonName(blankToNull(personName));
            s.setLocation(blankToNull(location));
            s.setDescription(blankToNull(description));
            s.setThumbnailPath(coverPath);
            s.setVideoPath(videoPath);
            s.setVideoOriginalName(FileStorageService.safeFilename(video.getOriginalFilename(), "story-video"));
            s.setCreatedBy(actor);
            s.setUpdatedBy(actor);
            s.setDisplayOrder((int) repository.count() + 1);
            s = repository.saveAndFlush(s);
            auditService.record(actor, "STORY_CREATED", "SuccessStory", s.getId(), s.getCategory() + " / " + s.getTitle(), ip);
            auditService.record(actor, "STORY_VIDEO_UPLOADED", "SuccessStory", s.getId(), s.getTitle(), ip);
            return toAdmin(s);
        } catch (RuntimeException ex) {
            storage.deleteQuietly(coverPath);
            storage.deleteQuietly(videoPath);
            throw ex;
        }
    }

    @Transactional
    public AdminStory update(Long id, StoryRequest req, User actor, String ip) {
        SuccessStory s = require(id);
        apply(s, req);
        s.setUpdatedBy(actor);
        repository.save(s);
        auditService.record(actor, "STORY_UPDATED", "SuccessStory", id, s.getCategory() + " / " + s.getTitle(), ip);
        return toAdmin(s);
    }

    @Transactional
    public AdminStory setPublished(Long id, boolean published, User actor, String ip) {
        SuccessStory s = require(id);
        if (published && !s.hasVideo()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upload a video or add a video link before publishing this story.");
        }
        s.setPublished(published);
        s.setUpdatedBy(actor);
        repository.save(s);
        auditService.record(actor, published ? "STORY_PUBLISHED" : "STORY_UNPUBLISHED", "SuccessStory", id, s.getTitle(), ip);
        return toAdmin(s);
    }

    @Transactional
    public void reorder(List<Long> orderedIds, User actor, String ip) {
        int order = 1;
        for (Long id : orderedIds) {
            SuccessStory s = require(id);
            s.setDisplayOrder(order++);
            repository.save(s);
        }
        auditService.record(actor, "STORIES_REORDERED", "SuccessStory", null, "New order " + orderedIds, ip);
    }

    @Transactional
    public AdminStory uploadVideo(Long id, MultipartFile file, User actor, String ip) {
        SuccessStory s = require(id);
        String previous = s.getVideoPath();
        s.setVideoPath(storage.store(file, FileStorageService.Kind.VIDEO));
        s.setVideoOriginalName(file.getOriginalFilename());
        s.setVideoUrl(null); // an uploaded file replaces any external link
        s.setUpdatedBy(actor);
        repository.save(s);
        storage.deleteQuietly(previous);
        auditService.record(actor, previous == null ? "STORY_VIDEO_UPLOADED" : "STORY_VIDEO_REPLACED", "SuccessStory", id, s.getTitle(), ip);
        return toAdmin(s);
    }

    @Transactional
    public AdminStory deleteVideo(Long id, User actor, String ip) {
        SuccessStory s = require(id);
        storage.deleteQuietly(s.getVideoPath());
        s.setVideoPath(null);
        s.setVideoOriginalName(null);
        if (!s.hasVideo()) {
            s.setPublished(false); // nothing left to show
        }
        s.setUpdatedBy(actor);
        repository.save(s);
        auditService.record(actor, "STORY_VIDEO_DELETED", "SuccessStory", id, s.getTitle(), ip);
        return toAdmin(s);
    }

    @Transactional
    public AdminStory uploadThumbnail(Long id, MultipartFile file, User actor, String ip) {
        SuccessStory s = require(id);
        String previous = s.getThumbnailPath();
        s.setThumbnailPath(storage.store(file, FileStorageService.Kind.IMAGE));
        s.setUpdatedBy(actor);
        repository.save(s);
        if (previous != null && previous.startsWith("images/")) {
            storage.deleteQuietly(previous);
        }
        auditService.record(actor, "STORY_THUMBNAIL_UPLOADED", "SuccessStory", id, s.getTitle(), ip);
        return toAdmin(s);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        SuccessStory s = require(id);
        storage.deleteQuietly(s.getVideoPath());
        if (s.getThumbnailPath() != null && s.getThumbnailPath().startsWith("images/")) {
            storage.deleteQuietly(s.getThumbnailPath());
        }
        repository.delete(s);
        auditService.record(actor, "STORY_DELETED", "SuccessStory", id, s.getCategory() + " / " + s.getTitle(), ip);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private SuccessStory require(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Story", id));
    }

    private static void apply(SuccessStory s, StoryRequest req) {
        s.setCategory(req.category());
        s.setTitle(req.title().trim());
        s.setPersonName(blankToNull(req.personName()));
        s.setLocation(blankToNull(req.location()));
        s.setDescription(blankToNull(req.description()));
        s.setVideoUrl(blankToNull(req.videoUrl()));
        if (req.displayOrder() != null) {
            s.setDisplayOrder(req.displayOrder());
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private void requireFile(MultipartFile file, FileStorageService.Kind kind, String missingMessage) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, missingMessage);
        storage.validate(file, kind);
    }

    private PublicStory toPublic(SuccessStory s) {
        String type = s.getVideoPath() != null && !s.getVideoPath().isBlank() ? "UPLOAD"
                : s.getVideoUrl() != null && !s.getVideoUrl().isBlank() ? "EMBED" : "NONE";
        return new PublicStory(s.getId(), s.getCategory(), s.getTitle(), s.getPersonName(), s.getLocation(),
                s.getDescription(), s.getThumbnailPath(), type, "EMBED".equals(type) ? s.getVideoUrl() : null);
    }

    private AdminStory toAdmin(SuccessStory s) {
        return new AdminStory(s.getId(), s.getCategory(), s.getTitle(), s.getPersonName(), s.getLocation(),
                s.getDescription(), s.getThumbnailPath(),
                s.getVideoPath() != null && !s.getVideoPath().isBlank(), s.getVideoOriginalName(), s.getVideoUrl(),
                s.isPublished(), s.getDisplayOrder(),
                s.getCreatedBy() == null ? null : s.getCreatedBy().getFullName(),
                s.getUpdatedBy() == null ? null : s.getUpdatedBy().getFullName(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
