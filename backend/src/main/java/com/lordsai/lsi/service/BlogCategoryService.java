package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.blog.BlogDtos.AdminCategory;
import com.lordsai.lsi.dto.blog.BlogDtos.CategoryRequest;
import com.lordsai.lsi.dto.blog.BlogDtos.PublicCategory;
import com.lordsai.lsi.entity.BlogCategory;
import com.lordsai.lsi.entity.Site;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.repository.BlogCategoryRepository;
import com.lordsai.lsi.repository.SiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class BlogCategoryService {

    private final BlogCategoryRepository repository;
    private final SiteRepository siteRepository;
    private final AuditService auditService;

    public BlogCategoryService(BlogCategoryRepository repository,
                               SiteRepository siteRepository,
                               AuditService auditService) {
        this.repository = repository;
        this.siteRepository = siteRepository;
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
    public List<AdminCategory> listAdmin(SiteCode site) {
        SiteCode targetSite = canonical(site);
        return repository.findBySiteSiteCodeOrderByDisplayOrderAscNameAsc(targetSite).stream()
                .map(this::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCategory get(Long id) {
        return toAdmin(require(id));
    }

    @Transactional
    public AdminCategory create(CategoryRequest req, User actor, String ip) {
        Site site = requireSite(req.site());
        String slug = resolveSlug(req.slug(), req.name());
        if (repository.existsBySiteIdAndSlug(site.getId(), slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this slug already exists for " + site.getSiteName() + ".");
        }

        BlogCategory cat = new BlogCategory();
        cat.setSite(site);
        cat.setName(req.name().trim());
        cat.setSlug(slug);
        cat.setDescription(req.description() == null ? null : req.description().trim());
        cat.setDisplayOrder(req.displayOrder() == null ? 0 : req.displayOrder());
        cat.setActive(req.active() == null || req.active());

        BlogCategory saved = repository.save(cat);
        auditService.record(actor, "BLOG_CATEGORY_CREATED", "BlogCategory", saved.getId(),
                "Created blog category '" + saved.getName() + "' for " + site.getSiteCode(), ip);

        return toAdmin(saved);
    }

    @Transactional
    public AdminCategory update(Long id, CategoryRequest req, User actor, String ip) {
        BlogCategory cat = require(id);
        Site site = req.site() != null ? requireSite(req.site()) : cat.getSite();
        String slug = resolveSlug(req.slug(), req.name());

        if (repository.existsBySiteIdAndSlugAndIdNot(site.getId(), slug, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Another category with this slug already exists for " + site.getSiteName() + ".");
        }

        cat.setSite(site);
        cat.setName(req.name().trim());
        cat.setSlug(slug);
        cat.setDescription(req.description() == null ? null : req.description().trim());
        if (req.displayOrder() != null) {
            cat.setDisplayOrder(req.displayOrder());
        }
        if (req.active() != null) {
            cat.setActive(req.active());
        }

        BlogCategory saved = repository.save(cat);
        auditService.record(actor, "BLOG_CATEGORY_UPDATED", "BlogCategory", saved.getId(),
                "Updated blog category '" + saved.getName() + "' (" + saved.getSlug() + ")", ip);

        return toAdmin(saved);
    }

    @Transactional
    public AdminCategory toggleActive(Long id, User actor, String ip) {
        BlogCategory cat = require(id);
        cat.setActive(!cat.isActive());
        BlogCategory saved = repository.save(cat);

        auditService.record(actor, "BLOG_CATEGORY_UPDATED", "BlogCategory", saved.getId(),
                (saved.isActive() ? "Activated" : "Deactivated") + " blog category '" + saved.getName() + "'", ip);

        return toAdmin(saved);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        BlogCategory cat = require(id);
        long count = repository.countBlogsByCategoryId(id);
        if (count > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Cannot delete category '" + cat.getName() + "' because " + count + " blog(s) are currently assigned to it. Reassign or delete those blogs first.");
        }

        repository.delete(cat);
        auditService.record(actor, "BLOG_CATEGORY_DELETED", "BlogCategory", id,
                "Deleted blog category '" + cat.getName() + "' (" + cat.getSlug() + ")", ip);
    }

    // ---- Public Operations -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicCategory> listPublic(SiteCode site) {
        SiteCode targetSite = canonical(site);
        return repository.findBySiteSiteCodeAndActiveTrueOrderByDisplayOrderAscNameAsc(targetSite).stream()
                .map(this::toPublic)
                .toList();
    }

    // ---- Helpers ---------------------------------------------------------------------------

    public BlogCategory require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Blog category not found."));
    }

    public Site requireSite(SiteCode siteCode) {
        SiteCode target = canonical(siteCode);
        return siteRepository.findBySiteCode(target)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid site: " + siteCode));
    }

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "category";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "category" : slug;
    }

    private String resolveSlug(String providedSlug, String name) {
        if (providedSlug != null && !providedSlug.isBlank()) {
            return slugify(providedSlug);
        }
        return slugify(name);
    }

    private AdminCategory toAdmin(BlogCategory c) {
        long count = repository.countBlogsByCategoryId(c.getId());
        return new AdminCategory(
                c.getId(),
                c.getSite().getSiteCode(),
                c.getName(),
                c.getSlug(),
                c.getDescription(),
                c.getDisplayOrder(),
                c.isActive(),
                count,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private PublicCategory toPublic(BlogCategory c) {
        return new PublicCategory(
                c.getId(),
                c.getName(),
                c.getSlug(),
                c.getDescription(),
                c.getDisplayOrder()
        );
    }
}

