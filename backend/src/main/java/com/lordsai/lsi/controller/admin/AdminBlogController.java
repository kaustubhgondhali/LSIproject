package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.blog.BlogDtos.AdminBlogDetail;
import com.lordsai.lsi.dto.blog.BlogDtos.AdminBlogSummary;
import com.lordsai.lsi.dto.blog.BlogDtos.BlogRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.BlogStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.BlogService;
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

@RestController
@RequestMapping("/api/admin/blogs")
public class AdminBlogController {

    private final BlogService blogService;
    private final UserService userService;

    public AdminBlogController(BlogService blogService, UserService userService) {
        this.blogService = blogService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<Page<AdminBlogSummary>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SiteCode site,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BlogStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ApiResponse.ok(blogService.searchAdmin(search, site, categoryId, status,
                PageRequest.of(Math.max(0, page), Math.min(size, 100))));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminBlogDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(blogService.getAdmin(id));
    }

    @PostMapping
    public ApiResponse<AdminBlogDetail> create(@Valid @RequestBody BlogRequest req,
                                               HttpServletRequest servletReq) {
        return ApiResponse.ok("Blog created successfully.",
                blogService.create(req, null, actor(), RequestUtil.clientIp(servletReq)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminBlogDetail> update(@PathVariable Long id,
                                               @Valid @RequestBody BlogRequest req,
                                               HttpServletRequest servletReq) {
        return ApiResponse.ok("Blog updated successfully.",
                blogService.update(id, req, null, actor(), RequestUtil.clientIp(servletReq)));
    }

    @PostMapping("/{id}/image")
    public ApiResponse<AdminBlogDetail> uploadImage(@PathVariable Long id,
                                                    @RequestParam("file") MultipartFile file,
                                                    HttpServletRequest servletReq) {
        return ApiResponse.ok("Featured image updated.",
                blogService.uploadFeaturedImage(id, file, actor(), RequestUtil.clientIp(servletReq)));
    }

    @PatchMapping("/{id}/publish")
    public ApiResponse<AdminBlogDetail> publish(@PathVariable Long id, HttpServletRequest servletReq) {
        return ApiResponse.ok("Blog published successfully. It is now live on the website.",
                blogService.setPublished(id, true, actor(), RequestUtil.clientIp(servletReq)));
    }

    @PatchMapping("/{id}/unpublish")
    public ApiResponse<AdminBlogDetail> unpublish(@PathVariable Long id, HttpServletRequest servletReq) {
        return ApiResponse.ok("Blog unpublished. It is no longer visible to visitors.",
                blogService.setPublished(id, false, actor(), RequestUtil.clientIp(servletReq)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest servletReq) {
        blogService.delete(id, actor(), RequestUtil.clientIp(servletReq));
        return ApiResponse.message("Blog deleted successfully.");
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}

