package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.blog.BlogDtos.PublicBlogDetail;
import com.lordsai.lsi.dto.blog.BlogDtos.PublicBlogSummary;
import com.lordsai.lsi.dto.blog.BlogDtos.PublicCategory;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.service.BlogCategoryService;
import com.lordsai.lsi.service.BlogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicBlogController {

    private final BlogService blogService;
    private final BlogCategoryService categoryService;

    public PublicBlogController(BlogService blogService, BlogCategoryService categoryService) {
        this.blogService = blogService;
        this.categoryService = categoryService;
    }

    /**
     * Lists published blogs strictly for the requested website (ACADEMY/SHARE_MARKET or MUTUAL_FUND).
     * Draft and unpublished blogs are never returned.
     */
    @GetMapping("/blogs")
    public ApiResponse<Page<PublicBlogSummary>> listBlogs(
            @RequestParam(defaultValue = "ACADEMY") SiteCode site,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(blogService.listPublic(site, categoryId, search,
                PageRequest.of(Math.max(0, page), Math.min(size, 50))));
    }

    /**
     * Retrieves published blog detail by slug or ID for the requested website.
     */
    @GetMapping("/blogs/{slugOrId}")
    public ApiResponse<PublicBlogDetail> getBlog(
            @PathVariable String slugOrId,
            @RequestParam(defaultValue = "ACADEMY") SiteCode site) {
        return ApiResponse.ok(blogService.getPublic(site, slugOrId));
    }

    /**
     * Active categories for the requested website.
     */
    @GetMapping("/blog-categories")
    public ApiResponse<List<PublicCategory>> listCategories(
            @RequestParam(defaultValue = "ACADEMY") SiteCode site) {
        return ApiResponse.ok(categoryService.listPublic(site));
    }
}

