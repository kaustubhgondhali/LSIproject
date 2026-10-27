package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.HomepageSliderImage;
import com.lordsai.lsi.entity.enums.SiteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HomepageSliderImageRepository extends JpaRepository<HomepageSliderImage, Long> {

    @Query("select i from HomepageSliderImage i where i.site.siteCode = :site order by i.displayOrder asc, i.id asc")
    List<HomepageSliderImage> findAllForSite(@Param("site") SiteCode site);

    @Query("select i from HomepageSliderImage i where i.site.siteCode = :site and i.active = true order by i.displayOrder asc, i.id asc")
    List<HomepageSliderImage> findActiveForSite(@Param("site") SiteCode site);
}
