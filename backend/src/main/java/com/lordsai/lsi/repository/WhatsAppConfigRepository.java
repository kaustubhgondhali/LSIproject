package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.WhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsAppConfigRepository extends JpaRepository<WhatsAppConfig, Long> {

    /** There is exactly one central configuration (or none). */
    Optional<WhatsAppConfig> findFirstByOrderByIdAsc();
}
