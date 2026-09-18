package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.BlogCategory;
import com.lordsai.lsi.entity.enums.SiteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlogCategoryRepository extends JpaRepository<BlogCategory, Long> {

    List<BlogCategory> findBySiteSiteCodeOrderByDisplayOrderAscNameAsc(SiteCode siteCode);

    List<BlogCategory> findBySiteSiteCodeAndActiveTrueOrderByDisplayOrderAscNameAsc(SiteCode siteCode);

    Optional<BlogCategory> findBySiteSiteCodeAndSlug(SiteCode siteCode, String slug);

    Optional<BlogCategory> findBySiteIdAndSlug(Long siteId, String slug);

    boolean existsBySiteIdAndSlug(Long siteId, String slug);

    boolean existsBySiteIdAndSlugAndIdNot(Long siteId, String slug, Long id);

    @Query("SELECT COUNT(b) FROM Blog b WHERE b.category.id = :categoryId")
    long countBlogsByCategoryId(@Param("categoryId") Long categoryId);
}

