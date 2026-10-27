package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.EmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailSettingsRepository extends JpaRepository<EmailSettings, Long> {

    /** There is at most one row; the lowest id wins if the table were ever seeded twice. */
    Optional<EmailSettings> findFirstByOrderByIdAsc();
}
