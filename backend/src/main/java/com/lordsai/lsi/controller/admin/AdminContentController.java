package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.admin.AdminDtos.SiteContentRequest;
import com.lordsai.lsi.dto.admin.AdminDtos.SiteContentResponse;
import com.lordsai.lsi.dto.admin.AdminDtos.SiteContentValueRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.SiteContentService;
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

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    private final SiteContentService contentService;
    private final UserService userService;

    public AdminContentController(SiteContentService contentService, UserService userService) {
        this.contentService = contentService;
        this.userService = userService;
    }

    @GetMapping("/{site}")
    public ApiResponse<List<SiteContentResponse>> list(@PathVariable SiteCode site) {
        return ApiResponse.ok(contentService.list(site));
    }

    @PutMapping("/{site}")
    public ApiResponse<SiteContentResponse> upsert(@PathVariable SiteCode site, @Valid @RequestBody SiteContentRequest body,
                                                   HttpServletRequest req) {
        return ApiResponse.ok("Content saved.", contentService.upsert(site, body, actor(), RequestUtil.clientIp(req)));
    }

    @PatchMapping("/items/{id}")
    public ApiResponse<SiteContentResponse> updateValue(@PathVariable Long id, @Valid @RequestBody SiteContentValueRequest body,
                                                        HttpServletRequest req) {
        return ApiResponse.ok("Content saved.", contentService.updateValue(id, body.contentValue(), actor(), RequestUtil.clientIp(req)));
    }

    @PostMapping("/items/{id}/image")
    public ApiResponse<SiteContentResponse> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                        HttpServletRequest req) {
        return ApiResponse.ok("Image uploaded.", contentService.uploadImage(id, file, actor(), RequestUtil.clientIp(req)));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest req) {
        contentService.delete(id, actor(), RequestUtil.clientIp(req));
        return ApiResponse.message("Content item deleted.");
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}
