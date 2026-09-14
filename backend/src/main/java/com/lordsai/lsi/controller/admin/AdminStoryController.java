package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.course.CourseDtos.ReorderRequest;
import com.lordsai.lsi.dto.story.StoryDtos.AdminStory;
import com.lordsai.lsi.dto.story.StoryDtos.PublishRequest;
import com.lordsai.lsi.dto.story.StoryDtos.StoryRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.SuccessStoryService;
import com.lordsai.lsi.service.UserService;
import com.lordsai.lsi.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

import java.util.List;

/** Success Stories management (ADMIN only — enforced by SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin/stories")
public class AdminStoryController {

    private final SuccessStoryService storyService;
    private final UserService userService;

    public AdminStoryController(SuccessStoryService storyService, UserService userService) {
        this.storyService = storyService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<AdminStory>> list() {
        return ApiResponse.ok(storyService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminStory> get(@PathVariable Long id) {
        return ApiResponse.ok(storyService.get(id));
    }

    @PostMapping
    public ApiResponse<AdminStory> create(@Valid @RequestBody StoryRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Story created. Upload a video, then publish it.",
                storyService.create(body, actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminStory> update(@PathVariable Long id, @Valid @RequestBody StoryRequest body, HttpServletRequest req) {
        return ApiResponse.ok("Story updated.", storyService.update(id, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/{id}/publish")
    public ApiResponse<AdminStory> publish(@PathVariable Long id, @RequestBody PublishRequest body, HttpServletRequest req) {
        return ApiResponse.ok(body.published() ? "Story published." : "Story unpublished.",
                storyService.setPublished(id, body.published(), actor(), RequestUtil.clientIp(req)));
    }

    @PutMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderRequest body, HttpServletRequest req) {
        storyService.reorder(body.orderedIds(), actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Order saved.");
    }

    @PostMapping("/{id}/video")
    public ApiResponse<AdminStory> uploadVideo(@PathVariable Long id, @RequestParam("file") MultipartFile file, HttpServletRequest req) {
        return ApiResponse.ok("Video uploaded.", storyService.uploadVideo(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/{id}/video")
    public ApiResponse<AdminStory> deleteVideo(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok("Video removed.", storyService.deleteVideo(id, actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/{id}/thumbnail")
    public ApiResponse<AdminStory> uploadThumbnail(@PathVariable Long id, @RequestParam("file") MultipartFile file, HttpServletRequest req) {
        return ApiResponse.ok("Thumbnail uploaded.", storyService.uploadThumbnail(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        storyService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Story deleted.");
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
