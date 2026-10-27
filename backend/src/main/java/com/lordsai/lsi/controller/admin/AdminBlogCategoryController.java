package com.lordsai.lsi.controller.admin;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.blog.BlogDtos.AdminCategory;
import com.lordsai.lsi.dto.blog.BlogDtos.CategoryRequest;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.security.CurrentUser;
import com.lordsai.lsi.service.BlogCategoryService;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/blog-categories")
public class AdminBlogCategoryController {

    private final BlogCategoryService categoryService;
    private final UserService userService;

    public AdminBlogCategoryController(BlogCategoryService categoryService, UserService userService) {
        this.categoryService = categoryService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<AdminCategory>> list(@RequestParam(required = false) SiteCode site) {
        return ApiResponse.ok(categoryService.listAdmin(site));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminCategory> get(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.get(id));
    }

    @PostMapping
    public ApiResponse<AdminCategory> create(@Valid @RequestBody CategoryRequest req, HttpServletRequest servletReq) {
        return ApiResponse.ok("Category created.",
                categoryService.create(req, actor(), RequestUtil.clientIp(servletReq)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCategory> update(@PathVariable Long id,
                                             @Valid @RequestBody CategoryRequest req,
                                             HttpServletRequest servletReq) {
        return ApiResponse.ok("Category updated.",
                categoryService.update(id, req, actor(), RequestUtil.clientIp(servletReq)));
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<AdminCategory> toggle(@PathVariable Long id, HttpServletRequest servletReq) {
        return ApiResponse.ok("Category status updated.",
                categoryService.toggleActive(id, actor(), RequestUtil.clientIp(servletReq)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest servletReq) {
        categoryService.delete(id, actor(), RequestUtil.clientIp(servletReq));
        return ApiResponse.message("Category deleted.");
    }

    private User actor() {
        return userService.requireUser(CurrentUser.require().id());
    }
}

