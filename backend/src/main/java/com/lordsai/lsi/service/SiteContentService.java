package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.admin.AdminDtos.SiteContentRequest;
import com.lordsai.lsi.dto.admin.AdminDtos.SiteContentResponse;
import com.lordsai.lsi.entity.Site;
import com.lordsai.lsi.entity.SiteContent;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.SiteCode;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.SiteContentRepository;
import com.lordsai.lsi.repository.SiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin-editable website copy for the Academy and Mutual Fund experiences. */
@Service
public class SiteContentService {

    private static final Set<String> TYPES = Set.of("TEXT", "HTML", "IMAGE_PATH", "URL");

    private final SiteRepository siteRepository;
    private final SiteContentRepository contentRepository;
    private final FileStorageService storage;
    private final AuditService auditService;

    public SiteContentService(SiteRepository siteRepository,
                              SiteContentRepository contentRepository,
                              FileStorageService storage,
                              AuditService auditService) {
        this.siteRepository = siteRepository;
        this.contentRepository = contentRepository;
        this.storage = storage;
        this.auditService = auditService;
    }

    /** Flat key -> value map for the public pages (only what they need to render). */
    @Transactional(readOnly = true)
    public Map<String, String> publicContent(SiteCode siteCode) {
        Map<String, String> out = new LinkedHashMap<>();
        contentRepository.findBySiteCode(siteCode).forEach(c -> out.put(c.getContentKey(), c.getContentValue()));
        return out;
    }

    @Transactional(readOnly = true)
    public List<SiteContentResponse> list(SiteCode siteCode) {
        return contentRepository.findBySiteCode(siteCode).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SiteContentResponse upsert(SiteCode siteCode, SiteContentRequest req, User actor, String ip) {
        if (!TYPES.contains(req.contentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Content type must be one of " + TYPES);
        }
        Site site = siteRepository.findBySiteCode(siteCode).orElseThrow(() -> ResourceNotFoundException.of("Site", siteCode));
        SiteContent c = contentRepository.findBySiteCodeAndKey(siteCode, req.contentKey()).orElseGet(() -> {
            SiteContent n = new SiteContent();
            n.setSite(site);
            n.setContentKey(req.contentKey().trim());
            return n;
        });
        boolean created = c.getId() == null;
        c.setLabel(req.label().trim());
        c.setContentValue(req.contentValue());
        c.setContentType(req.contentType());
        c.setUpdatedBy(actor);
        contentRepository.save(c);
        auditService.record(actor, created ? "CONTENT_CREATED" : "CONTENT_UPDATED", "SiteContent", c.getId(),
                siteCode + " / " + c.getContentKey(), ip);
        return toResponse(c);
    }

    @Transactional
    public SiteContentResponse updateValue(Long id, String value, User actor, String ip) {
        SiteContent c = contentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Content", id));
        c.setContentValue(value);
        c.setUpdatedBy(actor);
        contentRepository.save(c);
        auditService.record(actor, "CONTENT_UPDATED", "SiteContent", id, c.getSite().getSiteCode() + " / " + c.getContentKey(), ip);
        return toResponse(c);
    }

    @Transactional
    public SiteContentResponse uploadImage(Long id, MultipartFile file, User actor, String ip) {
        SiteContent c = contentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Content", id));
        if (!"IMAGE_PATH".equals(c.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This content item is not an image.");
        }
        String previous = c.getContentValue();
        c.setContentValue(storage.store(file, FileStorageService.Kind.IMAGE));
        c.setUpdatedBy(actor);
        contentRepository.save(c);
        if (previous != null && previous.startsWith("images/")) {
            storage.deleteQuietly(previous);
        }
        auditService.record(actor, "CONTENT_IMAGE_UPLOADED", "SiteContent", id, c.getContentKey(), ip);
        return toResponse(c);
    }

    @Transactional
    public void delete(Long id, User actor, String ip) {
        SiteContent c = contentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Content", id));
        contentRepository.delete(c);
        auditService.record(actor, "CONTENT_DELETED", "SiteContent", id, c.getSite().getSiteCode() + " / " + c.getContentKey(), ip);
    }

    private SiteContentResponse toResponse(SiteContent c) {
        return new SiteContentResponse(c.getId(), c.getSite().getSiteCode().name(), c.getContentKey(), c.getLabel(),
                c.getContentValue(), c.getContentType(),
                c.getUpdatedBy() == null ? null : c.getUpdatedBy().getFullName(), c.getUpdatedAt());
    }
}
