package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Site;
import com.lordsai.lsi.entity.enums.SiteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    Optional<Site> findBySiteCode(SiteCode siteCode);
}
