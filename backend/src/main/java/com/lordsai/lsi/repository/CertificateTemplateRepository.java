package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.CertificateTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, Long> {

    List<CertificateTemplate> findAllByOrderByCreatedAtDesc();

    Optional<CertificateTemplate> findFirstByActiveTrueOrderByUpdatedAtDesc();

    List<CertificateTemplate> findByActiveTrue();
}
