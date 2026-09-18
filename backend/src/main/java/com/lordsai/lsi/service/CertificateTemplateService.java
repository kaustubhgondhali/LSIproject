package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.exam.CertificateDtos;
import com.lordsai.lsi.dto.exam.CertificateDtos.TemplateResponse;
import com.lordsai.lsi.dto.exam.CertificateDtos.TemplateStatus;
import com.lordsai.lsi.entity.CertificateTemplate;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.CertificateRepository;
import com.lordsai.lsi.repository.CertificateTemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Main Admin -> Certificates: upload / replace / activate certificate background designs.
 * The default Lord Sai certificate is built into the application (templates/certificate/
 * certificate.html with the Lord Sai logo); it is used whenever no uploaded design is active and
 * can never be removed. Activating an uploaded design only affects certificates issued afterwards.
 */
@Service
public class CertificateTemplateService {

    private final CertificateTemplateRepository templateRepository;
    private final CertificateRepository certificateRepository;
    private final FileStorageService storage;
    private final AuditService auditService;

    public CertificateTemplateService(CertificateTemplateRepository templateRepository,
                                      CertificateRepository certificateRepository,
                                      FileStorageService storage,
                                      AuditService auditService) {
        this.templateRepository = templateRepository;
        this.certificateRepository = certificateRepository;
        this.storage = storage;
        this.auditService = auditService;
    }

    /** The design new certificates will use: an active uploaded template, or empty = default Lord Sai. */
    @Transactional(readOnly = true)
    public Optional<CertificateTemplate> activeTemplate() {
        return templateRepository.findFirstByActiveTrueOrderByUpdatedAtDesc()
                .filter(t -> storage.exists(t.getImagePath()));   // a missing file falls back to the default
    }

    @Transactional(readOnly = true)
    public TemplateStatus status() {
        Optional<CertificateTemplate> active = activeTemplate();
        List<TemplateResponse> all = templateRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
        return new TemplateStatus(active.isEmpty(),
                active.map(CertificateTemplate::getName).orElse(CertificateDtos.DEFAULT_TEMPLATE_NAME),
                active.map(CertificateTemplate::getId).orElse(null),
                CertificateDtos.DEFAULT_TEMPLATE_NAME, all);
    }

    @Transactional(readOnly = true)
    public CertificateTemplate require(Long id) {
        return templateRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Certificate template", id));
    }

    /** Uploads a new design. It becomes the active template immediately when {@code activate} is true. */
    @Transactional
    public TemplateResponse upload(String name, MultipartFile file, boolean activate, User actor, String ip) {
        storage.validate(file, FileStorageService.Kind.CERT_TEMPLATE);
        if (!FileStorageService.looksLike(file, FileStorageService.Kind.CERT_TEMPLATE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid JPG or PNG image.");
        }
        String path = storage.store(file, FileStorageService.Kind.CERT_TEMPLATE);
        CertificateTemplate t = new CertificateTemplate();
        String safeName = name == null || name.isBlank()
                ? FileStorageService.safeFilename(file.getOriginalFilename(), "Certificate template") : name.trim();
        t.setName(safeName.length() > 150 ? safeName.substring(0, 150) : safeName);
        t.setImagePath(path);
        t.setOriginalName(FileStorageService.safeFilename(file.getOriginalFilename(), "template"));
        t.setContentType(file.getContentType() == null ? null : file.getContentType().toLowerCase(Locale.ROOT));
        t.setSizeBytes(file.getSize());
        t.setUploadedBy(actor);
        t.setActive(false);
        t = templateRepository.save(t);
        if (activate) {
            activateInternal(t);
        }
        auditService.record(actor, "CERTIFICATE_TEMPLATE_UPLOADED", "CertificateTemplate", t.getId(),
                t.getName() + (activate ? " (activated)" : ""), ip);
        return toResponse(t);
    }

    /** Makes one uploaded design the active one (deactivating any other). */
    @Transactional
    public TemplateResponse activate(Long id, User actor, String ip) {
        CertificateTemplate t = require(id);
        if (!storage.exists(t.getImagePath())) {
            throw new ApiException(HttpStatus.CONFLICT, "The image file for this template is missing. Upload it again.");
        }
        activateInternal(t);
        auditService.record(actor, "CERTIFICATE_TEMPLATE_ACTIVATED", "CertificateTemplate", t.getId(), t.getName(), ip);
        return toResponse(t);
    }

    /** Deactivates every uploaded design -> new certificates use the default Lord Sai certificate. */
    @Transactional
    public TemplateStatus useDefault(User actor, String ip) {
        templateRepository.findByActiveTrue().forEach(t -> {
            t.setActive(false);
            templateRepository.save(t);
        });
        auditService.record(actor, "CERTIFICATE_TEMPLATE_DEFAULT", "CertificateTemplate", null,
                "Default Lord Sai certificate activated", ip);
        return status();
    }

    /**
     * Removes an uploaded design. Certificates already issued with it keep their stored PDF; if
     * that PDF ever has to be regenerated it falls back to the default design. Removing the active
     * design falls back to the default automatically.
     */
    public record DeleteOutcome(boolean deleted, String message, TemplateStatus status) {
    }

    @Transactional
    public DeleteOutcome delete(Long id, User actor, String ip) {
        CertificateTemplate t = require(id);
        long issued = certificateRepository.countByTemplateId(id);
        if (issued > 0) {
            // Keep the row (and file) so issued certificates stay reproducible; just retire it.
            t.setActive(false);
            templateRepository.save(t);
            auditService.record(actor, "CERTIFICATE_TEMPLATE_RETIRED", "CertificateTemplate", id,
                    t.getName() + " retired (" + issued + " certificate(s) issued with it)", ip);
            return new DeleteOutcome(false, issued + " certificate(s) were issued with this design, so it was deactivated "
                    + "instead of deleted. New certificates now use the default Lord Sai certificate.", status());
        }
        templateRepository.delete(t);
        storage.deleteQuietly(t.getImagePath());
        auditService.record(actor, "CERTIFICATE_TEMPLATE_DELETED", "CertificateTemplate", id, t.getName(), ip);
        return new DeleteOutcome(true, "Template deleted. New certificates use the default Lord Sai certificate unless another design is active.", status());
    }

    /** The uploaded image as a data URI for the PDF renderer (never exposed as a public URL). */
    public String imageDataUri(CertificateTemplate t) {
        if (t == null || !storage.exists(t.getImagePath())) {
            return null;
        }
        String type = t.getContentType() != null && t.getContentType().contains("png") ? "image/png" : "image/jpeg";
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(storage.readBytes(t.getImagePath()));
    }

    private void activateInternal(CertificateTemplate target) {
        templateRepository.findByActiveTrue().forEach(other -> {
            if (!other.getId().equals(target.getId())) {
                other.setActive(false);
                templateRepository.save(other);
            }
        });
        target.setActive(true);
        templateRepository.save(target);
    }

    public TemplateResponse toResponse(CertificateTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getOriginalName(), t.getContentType(), t.getSizeBytes(),
                t.isActive(), certificateRepository.countByTemplateId(t.getId()),
                t.getUploadedBy() == null ? null : t.getUploadedBy().getFullName(), t.getCreatedAt());
    }
}
