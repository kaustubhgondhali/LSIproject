package com.lordsai.lsi.dto.blog;

import com.lordsai.lsi.entity.enums.BlogStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class BlogDtos {

    private BlogDtos() { }

    // ---- Category DTOs ---------------------------------------------------------------------

    public record CategoryRequest(
            SiteCode site,
            @NotBlank(message = "Category name is required.")
            @Size(max = 100, message = "Category name cannot exceed 100 characters.")
            String name,
            @Size(max = 120, message = "Slug cannot exceed 120 characters.")
            String slug,
            @Size(max = 255, message = "Description cannot exceed 255 characters.")
            String description,
            Integer displayOrder,
            Boolean active
    ) { }

    public record AdminCategory(
            Long id,
            SiteCode site,
            String name,
            String slug,
            String description,
            int displayOrder,
            boolean active,
            long blogCount,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record PublicCategory(
            Long id,
            String name,
            String slug,
            String description,
            int displayOrder
    ) { }

    // ---- Blog DTOs -------------------------------------------------------------------------

    public record BlogRequest(
            SiteCode site,
            @NotBlank(message = "Blog title is required.")
            @Size(max = 255, message = "Title cannot exceed 255 characters.")
            String title,
            @Size(max = 280, message = "Slug cannot exceed 280 characters.")
            String slug,
            Long categoryId,
            @NotBlank(message = "Short description is required.")
            @Size(max = 1000, message = "Short description cannot exceed 1000 characters.")
            String shortDescription,
            @NotBlank(message = "Blog content is required.")
            String content,
            String featuredImagePath,
            @Size(max = 150, message = "Author cannot exceed 150 characters.")
            String author,
            @Size(max = 500, message = "Tags cannot exceed 500 characters.")
            String tags,
            BlogStatus status,
            @Size(max = 255, message = "SEO Title cannot exceed 255 characters.")
            String seoTitle,
            @Size(max = 500, message = "SEO Description cannot exceed 500 characters.")
            String seoDescription,
            @Size(max = 500, message = "SEO Keywords cannot exceed 500 characters.")
            String seoKeywords
    ) { }

    public record AdminBlogSummary(
            Long id,
            SiteCode site,
            String title,
            String slug,
            Long categoryId,
            String categoryName,
            String shortDescription,
            String featuredImagePath,
            String author,
            String tags,
            BlogStatus status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record AdminBlogDetail(
            Long id,
            SiteCode site,
            String title,
            String slug,
            Long categoryId,
            String categoryName,
            String shortDescription,
            String content,
            String featuredImagePath,
            String author,
            String tags,
            BlogStatus status,
            String seoTitle,
            String seoDescription,
            String seoKeywords,
            Instant publishedAt,
            String createdByName,
            String updatedByName,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record PublicBlogSummary(
            Long id,
            SiteCode site,
            String title,
            String slug,
            Long categoryId,
            String categoryName,
            String shortDescription,
            String featuredImagePath,
            String author,
            String tags,
            Instant publishedAt
    ) { }

    public record PublicBlogDetail(
            Long id,
            SiteCode site,
            String title,
            String slug,
            Long categoryId,
            String categoryName,
            String shortDescription,
            String content,
            String featuredImagePath,
            String author,
            String tags,
            String seoTitle,
            String seoDescription,
            String seoKeywords,
            Instant publishedAt
    ) { }
}

