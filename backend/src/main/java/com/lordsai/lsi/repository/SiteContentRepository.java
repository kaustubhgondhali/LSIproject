package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.SiteContent;
import com.lordsai.lsi.entity.enums.SiteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SiteContentRepository extends JpaRepository<SiteContent, Long> {

    @Query("select c from SiteContent c where c.site.siteCode = :siteCode order by c.contentKey")
    List<SiteContent> findBySiteCode(@Param("siteCode") SiteCode siteCode);

    @Query("select c from SiteContent c where c.site.siteCode = :siteCode and c.contentKey = :key")
    Optional<SiteContent> findBySiteCodeAndKey(@Param("siteCode") SiteCode siteCode, @Param("key") String key);
}
