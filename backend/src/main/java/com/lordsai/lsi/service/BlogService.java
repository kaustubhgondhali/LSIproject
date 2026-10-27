package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.blog.BlogDtos.AdminBlogDetail;
import com.lordsai.lsi.dto.blog.BlogDtos.AdminBlogSummary;
import com.lordsai.lsi.dto.blog.BlogDtos.BlogRequest;
import com.lordsai.lsi.dto.blog.BlogDtos.PublicBlogDetail;
import com.lordsai.lsi.dto.blog.BlogDtos.PublicBlogSummary;
import com.lordsai.lsi.entity.Blog;
import com.lordsai.lsi.entity.BlogCategory;
import com.lordsai.lsi.entity.Site;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.BlogStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.BlogCategoryRepository;
import com.lordsai.lsi.repository.BlogRepository;
import com.lordsai.lsi.repository.SiteRepository;
import com.lordsai.lsi.util.HtmlSanitizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

@Service
public class BlogService {

    private final BlogRepository repository;
    private final BlogCategoryRepository categoryRepository;
    private final SiteRepository siteRepository;
    private final FileStorageService storage;
    private final AuditService auditService;

    public BlogService(BlogRepository repository,
                       BlogCategoryRepository categoryRepository,
                       SiteRepository siteRepository,
                       FileStorageService storage,
                       AuditService auditService) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
        this.storage = storage;
        this.auditService = auditService;
    }

    public static SiteCode canonical(SiteCode site) {
        if (site == SiteCode.SHARE_MARKET) {
            return SiteCode.ACADEMY;
        }
        return site == null ? SiteCode.ACADEMY : site;
    }

    // ---- Admin Operations ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AdminBlogSummary> searchAdmin(String search, SiteCode site, Long categoryId, BlogStatus status, Pageable pageable) {
        SiteCode targetSite = site != null ? canonical(site) : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return repository.searchAdmin(targetSite, status, categoryId, cleanSearch, pageable)
                .map(this::toAdminSummary);
    }

    @Transactional(readOnly = true)
    public AdminBlogDetail getAdmin(Long id) {
        return toAdminDetail(require(id));
    }

    @Transactional
    public AdminBlogDetail create(BlogRequest req, MultipartFile image, User actor, String ip) {
        Site site = requireSite(req.site());
        String slug = resolveSlug(req.slug(), req.title());
        if (repository.existsBySiteIdAndSlug(site.getId(), slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "A blog with this slug already exists for " + site.getSiteName() + ".");
        }

        BlogCategory category = req.categoryId() != null ? requireCategory(req.categoryId(), site.getId()) : null;

        Blog blog = new Blog();
        blog.setSite(site);
        blog.setCategory(category);
        blog.setTitle(req.title().trim());
        blog.setSlug(slug);
        blog.setShortDescription(req.shortDescription().trim());
        blog.setContent(HtmlSanitizer.sanitize(req.content()));
        blog.setAuthor(req.author() != null && !req.author().isBlank() ? req.author().trim() : "Lord Sai Team");
        blog.setTags(req.tags() != null ? req.tags().trim() : null);

        BlogStatus status = req.status() != null ? req.status() : BlogStatus.DRAFT;
        blog.setStatus(status);
        if (status == BlogStatus.PUBLISHED) {
            blog.setPublishedAt(Instant.now());
        }

        blog.setSeoTitle(req.seoTitle() != null ? req.seoTitle().trim() : null);
        blog.setSeoDescription(req.seoDescription() != null ? req.seoDescription().trim() : null);
        blog.setSeoKeywords(req.seoKeywords() != null ? req.seoKeywords().trim() : null);

        if (image != null && !image.isEmpty()) {
            blog.setFeaturedImagePath(storage.store(image, FileStorageService.Kind.IMAGE));
        } else if (req.featuredImagePath() != null && !req.featuredImagePath().isBlank()) {
            blog.setFeaturedImagePath(req.featuredImagePath().trim());
        }

        blog.setCreatedBy(actor);
        blog.setUpdatedBy(actor);

        Blog saved = repository.save(blog);
        auditService.record(actor, "BLOG_CREATED", "Blog", saved.getId(),
                "Created blog '" + saved.getTitle() + "' [" + saved.getStatus() + "] for " + site.getSiteCode(), ip);

        if (saved.getStatus() == BlogStatus.PUBLISHED) {
            auditService.record(actor, "BLOG_PUBLISHED", "Blog", saved.getId(),
                    "Published blog '" + saved.getTitle() + "'", ip);
        }

        return toAdminDetail(saved);
    }

    @Transactional
    public AdminBlogDetail update(Long id, BlogRequest req, MultipartFile image, User actor, String ip) {
        Blog blog = require(id);
        Site site = req.site() != null ? requireSite(req.site()) : blog.getSite();
        String slug = resolveSlug(req.slug(), req.title());

        if (repository.existsBySiteIdAndSlugAndIdNot(site.getId(), slug, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Another blog with this slug already exists for " + site.getSiteName() + ".");
        }

        BlogCategory category = req.categoryId() != null ? requireCategory(req.categoryId(), site.getId()) : null;

        blog.setSite(site);
        blog.setCategory(category);
        blog.setTitle(req.title().trim());
        blog.setSlug(slug);
        blog.setShortDescription(req.shortDescription().trim());
        blog.setContent(HtmlSanitizer.sanitize(req.content()));

        if (req.author() != null && !req.author().isBlank()) {
            blog.setAuthor(req.author().trim());
        }
        blog.setTags(req.tags() != null ? req.tags().trim() : null);

        if (req.status() != null && req.status() != blog.getStatus()) {
            blog.setStatus(req.status());
            if (req.status() == BlogStatus.PUBLISHED && blog.getPublishedAt() == null) {
                blog.setPublishedAt(Instant.now());
            }
        }

        blog.setSeoTitle(req.seoTitle() != null ? req.seoTitle().trim() : null);
        blog.setSeoDescription(req.seoDescription() != null ? req.seoDescription().trim() : null);
        blog.setSeoKeywords(req.seoKeywords() != null ? req.seoKeywords().trim() : null);

        if (image != null && !image.isEmpty()) {
            String oldImg = blog.getFeaturedImagePath();
            blog.setFeaturedImagePath(storage.store(image, FileStorageService.Kind.IMAGE));
            if (oldImg != null && oldImg.startsWith("images/")) {
                storage.deleteQuietly(oldImg);
            }
            auditService.record(actor, "BLOG_IMAGE_UPDATED", "Blog", blog.getId(),
                    "Updated featured image for blog '" + blog.getTitle() + "'", ip);
        } else if (req.featuredImagePath() != null) {
            blog.setFeaturedImagePath(req.featuredImagePath().trim());
        }

        blog.setUpdatedBy(actor);
        Blog saved = repository.save(blog);

        auditService.record(actor, "BLOG_UPDATED", "Blog", saved.getId(),
                "Updated blog '" + saved.getTitle() + "' (" + saved.getSlug() + ")", ip);

        return toAdminDetail(saved);
    }

    @Transactional
    public AdminBlogDetail uploadFeaturedImage(Long id, MultipartFile file, User actor, String ip) {
        Blog blog = require(id);
        String oldImg = blog.getFeaturedImagePath();
        String storedPath = storage.store(file, FileStorageService.Kind.IMAGE);
        blog.setFeaturedImagePath(storedPath);
        blog.setUpdatedBy(actor);
        Blog saved = repository.save(blog);

        if (oldImg != null && oldImg.startsWith("images/")) {
            storage.deleteQuietly(oldImg);
        }

        auditService.record(actor, "BLOG_IMAGE_UPDATED", "Blog", saved.getId(),
                "Uploaded new featured image for blog '" + saved.getTitle() + "'", ip);

        return toAdminDetail(saved);
    }

    @Transactional
    public AdminBlogDetail setPublished(Long id, boolean publish, User actor, String ip) {
        Blog blog = require(id);
        BlogStatus newStatus = publish ? BlogStatus.PUBLISHED : BlogStatus.UNPUBLISHED;
        if (blog.getStatus() == newStatus) {
            return toAdminDetail(blog);
        }

        blog.setStatus(newStatus);
        if (publish && blog.getPublishedAt() == null) {
            blog.setPublishedAt(Instant.now());
        }
        blog.setUpdatedBy(actor);
        Blog saved = repository.save(blog);

        String action = publish ? "BLOG_PUBLISHED" : "BLOG_UNPUBLISHED";
        auditService.record(actor, action, "Blog", saved.getId(),
                (publish ? "Published" : "Unpublished") + " blog '" + saved.getTitle() + "'", ip);

        return toAdminDetail(saved);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        Blog blog = require(id);
        String title = blog.getTitle();
        String img = blog.getFeaturedImagePath();

        repository.delete(blog);

        if (img != null && img.startsWith("images/")) {
            storage.deleteQuietly(img);
        }

        auditService.record(actor, "BLOG_DELETED", "Blog", id,
                "Deleted blog '" + title + "'", ip);
    }

    // ---- Public Operations -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<PublicBlogSummary> listPublic(SiteCode site, Long categoryId, String search, Pageable pageable) {
        SiteCode targetSite = canonical(site);
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return repository.searchPublic(targetSite, BlogStatus.PUBLISHED, categoryId, cleanSearch, pageable)
                .map(this::toPublicSummary);
    }

    @Transactional(readOnly = true)
    public PublicBlogDetail getPublic(SiteCode site, String slugOrId) {
        SiteCode targetSite = canonical(site);
        Blog blog;
        try {
            Long id = Long.parseLong(slugOrId);
            blog = repository.findBySiteSiteCodeAndIdAndStatus(targetSite, id, BlogStatus.PUBLISHED)
                    .orElseGet(() -> repository.findBySiteSiteCodeAndSlugAndStatus(targetSite, slugOrId, BlogStatus.PUBLISHED)
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Article not found.")));
        } catch (NumberFormatException ignored) {
            blog = repository.findBySiteSiteCodeAndSlugAndStatus(targetSite, slugOrId, BlogStatus.PUBLISHED)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Article not found."));
        }
        return toPublicDetail(blog);
    }

    // ---- Helpers ---------------------------------------------------------------------------

    public Blog require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Blog not found."));
    }

    private Site requireSite(SiteCode siteCode) {
        SiteCode target = canonical(siteCode);
        return siteRepository.findBySiteCode(target)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid site: " + siteCode));
    }

    private BlogCategory requireCategory(Long categoryId, Long siteId) {
        BlogCategory cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Selected category not found."));
        if (!cat.getSite().getId().equals(siteId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The selected category does not belong to the chosen website.");
        }
        return cat;
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "blog-" + System.currentTimeMillis();
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "blog-" + System.currentTimeMillis() : slug;
    }

    private String resolveSlug(String providedSlug, String title) {
        if (providedSlug != null && !providedSlug.isBlank()) {
            return slugify(providedSlug);
        }
        return slugify(title);
    }

    private AdminBlogSummary toAdminSummary(Blog b) {
        return new AdminBlogSummary(
                b.getId(),
                b.getSite().getSiteCode(),
                b.getTitle(),
                b.getSlug(),
                b.getCategory() != null ? b.getCategory().getId() : null,
                b.getCategory() != null ? b.getCategory().getName() : "Uncategorized",
                b.getShortDescription(),
                b.getFeaturedImagePath(),
                b.getAuthor(),
                b.getTags(),
                b.getStatus(),
                b.getPublishedAt(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }

    private AdminBlogDetail toAdminDetail(Blog b) {
        return new AdminBlogDetail(
                b.getId(),
                b.getSite().getSiteCode(),
                b.getTitle(),
                b.getSlug(),
                b.getCategory() != null ? b.getCategory().getId() : null,
                b.getCategory() != null ? b.getCategory().getName() : "Uncategorized",
                b.getShortDescription(),
                b.getContent(),
                b.getFeaturedImagePath(),
                b.getAuthor(),
                b.getTags(),
                b.getStatus(),
                b.getSeoTitle(),
                b.getSeoDescription(),
                b.getSeoKeywords(),
                b.getPublishedAt(),
                b.getCreatedBy() != null ? b.getCreatedBy().getFullName() : "Admin",
                b.getUpdatedBy() != null ? b.getUpdatedBy().getFullName() : "Admin",
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }

    private PublicBlogSummary toPublicSummary(Blog b) {
        return new PublicBlogSummary(
                b.getId(),
                b.getSite().getSiteCode(),
                b.getTitle(),
                b.getSlug(),
                b.getCategory() != null ? b.getCategory().getId() : null,
                b.getCategory() != null ? b.getCategory().getName() : "General",
                b.getShortDescription(),
                b.getFeaturedImagePath(),
                b.getAuthor(),
                b.getTags(),
                b.getPublishedAt() != null ? b.getPublishedAt() : b.getCreatedAt()
        );
    }

    private PublicBlogDetail toPublicDetail(Blog b) {
        return new PublicBlogDetail(
                b.getId(),
                b.getSite().getSiteCode(),
                b.getTitle(),
                b.getSlug(),
                b.getCategory() != null ? b.getCategory().getId() : null,
                b.getCategory() != null ? b.getCategory().getName() : "General",
                b.getShortDescription(),
                b.getContent(),
                b.getFeaturedImagePath(),
                b.getAuthor(),
                b.getTags(),
                b.getSeoTitle(),
                b.getSeoDescription(),
                b.getSeoKeywords(),
                b.getPublishedAt() != null ? b.getPublishedAt() : b.getCreatedAt()
        );
    }
}

