package com.lordsai.lsi.entity;

import com.lordsai.lsi.entity.enums.SiteCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The two managed website experiences: the Academy and the Mutual Fund business. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sites")
public class Site extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "site_code", nullable = false, length = 30, unique = true)
    private SiteCode siteCode;

    @Column(name = "site_name", nullable = false, length = 150)
    private String siteName;

    @Column(nullable = false)
    private boolean active = true;
}
