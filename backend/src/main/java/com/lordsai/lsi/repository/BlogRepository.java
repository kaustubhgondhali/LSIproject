package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Blog;
import com.lordsai.lsi.entity.enums.BlogStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    @Query("""
        SELECT b FROM Blog b
        LEFT JOIN FETCH b.category c
        LEFT JOIN FETCH b.site s
        WHERE (:site IS NULL OR s.siteCode = :site)
          AND (:status IS NULL OR b.status = :status)
          AND (:categoryId IS NULL OR (c IS NOT NULL AND c.id = :categoryId))
          AND (:search IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(b.shortDescription) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(b.author) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(b.tags) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY b.updatedAt DESC
    """)
    Page<Blog> searchAdmin(@Param("site") SiteCode site,
                           @Param("status") BlogStatus status,
                           @Param("categoryId") Long categoryId,
                           @Param("search") String search,
                           Pageable pageable);

    @Query("""
        SELECT b FROM Blog b
        LEFT JOIN FETCH b.category c
        JOIN b.site s
        WHERE s.siteCode = :site
          AND b.status = :status
          AND (:categoryId IS NULL OR (c IS NOT NULL AND c.id = :categoryId))
          AND (:search IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(b.shortDescription) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(b.author) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(b.tags) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY b.publishedAt DESC, b.createdAt DESC
    """)
    Page<Blog> searchPublic(@Param("site") SiteCode site,
                            @Param("status") BlogStatus status,
                            @Param("categoryId") Long categoryId,
                            @Param("search") String search,
                            Pageable pageable);

    Optional<Blog> findBySiteSiteCodeAndSlugAndStatus(SiteCode siteCode, String slug, BlogStatus status);

    Optional<Blog> findBySiteSiteCodeAndIdAndStatus(SiteCode siteCode, Long id, BlogStatus status);

    boolean existsBySiteIdAndSlug(Long siteId, String slug);

    boolean existsBySiteIdAndSlugAndIdNot(Long siteId, String slug, Long id);
}

