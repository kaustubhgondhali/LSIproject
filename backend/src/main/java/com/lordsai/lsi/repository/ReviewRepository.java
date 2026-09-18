package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Review;
import com.lordsai.lsi.entity.enums.ReviewStatus;
import com.lordsai.lsi.entity.enums.SiteCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("select r from Review r where r.site.siteCode = :site and r.status = com.lordsai.lsi.entity.enums.ReviewStatus.APPROVED "
            + "order by r.approvedAt desc, r.id desc")
    List<Review> findApproved(@Param("site") SiteCode site);

    @Query("select r from Review r where (:status is null or r.status = :status) and (:site is null or r.site.siteCode = :site) "
            + "order by case when r.status = com.lordsai.lsi.entity.enums.ReviewStatus.PENDING then 0 else 1 end, r.createdAt desc")
    Page<Review> search(@Param("status") ReviewStatus status, @Param("site") SiteCode site, Pageable pageable);

    long countByStatus(ReviewStatus status);

    @Query("select count(r) from Review r where lower(r.email) = lower(:email) and r.site.siteCode = :site "
            + "and r.status = com.lordsai.lsi.entity.enums.ReviewStatus.PENDING")
    long countPendingByEmail(@Param("email") String email, @Param("site") SiteCode site);

    @Query("select count(r) from Review r where lower(r.email) = lower(:email) and r.createdAt > :since")
    long countRecentByEmail(@Param("email") String email, @Param("since") Instant since);
}
