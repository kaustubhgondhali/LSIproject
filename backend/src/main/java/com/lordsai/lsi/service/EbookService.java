package com.lordsai.lsi.service;

import com.lordsai.lsi.dto.ebook.EbookDtos.AdminEbook;
import com.lordsai.lsi.dto.ebook.EbookDtos.EbookRequest;
import com.lordsai.lsi.dto.ebook.EbookDtos.PublicEbook;
import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.EbookStatus;
import com.lordsai.lsi.entity.enums.EntitlementStatus;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.repository.EbookEntitlementRepository;
import com.lordsai.lsi.repository.EbookRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Ebook catalogue management (Main Admin) and the public store listing. Mirrors CourseService. */
@Service
public class EbookService {

    private final EbookRepository ebookRepository;
    private final EbookEntitlementRepository entitlementRepository;
    private final AuditService auditService;
    private final FileStorageService storage;

    public EbookService(EbookRepository ebookRepository,
                        EbookEntitlementRepository entitlementRepository,
                        AuditService auditService,
                        FileStorageService storage) {
        this.ebookRepository = ebookRepository;
        this.entitlementRepository = entitlementRepository;
        this.auditService = auditService;
        this.storage = storage;
    }

    // ---- Public ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PublicEbook> listPublic() {
        return ebookRepository.findByStatusOrderByDisplayOrderAscTitleAsc(EbookStatus.ACTIVE)
                .stream().map(this::toPublic).toList();
    }

    @Transactional(readOnly = true)
    public PublicEbook getPublic(Long id) {
        Ebook ebook = ebookRepository.findById(id)
                .filter(e -> e.getStatus() == EbookStatus.ACTIVE)
                .orElseThrow(() -> ResourceNotFoundException.of("Ebook", id));
        return toPublic(ebook);
    }

    @Transactional(readOnly = true)
    public Ebook requireEbook(Long id) {
        return ebookRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Ebook", id));
    }

    /** Only an ACTIVE ebook with an uploaded file can be sold. The price is read here, never from the client. */
    @Transactional(readOnly = true)
    public Ebook requirePurchasableEbook(Long id) {
        Ebook ebook = requireEbook(id);
        if (ebook.getStatus() != EbookStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This ebook is not available for purchase right now.");
        }
        if (ebook.getPdfPath() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This ebook is not available for purchase yet.");
        }
        return ebook;
    }

    // ---- Admin ---------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminEbook> listAdmin() {
        return ebookRepository.findAllByOrderByDisplayOrderAscTitleAsc().stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public AdminEbook getAdmin(Long id) {
        return toAdmin(requireEbook(id));
    }

    @Transactional
    public AdminEbook create(EbookRequest req, User actor, String ip) {
        if (ebookRepository.existsByEbookCodeIgnoreCase(req.ebookCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "An ebook with this code already exists.");
        }
        validatePricing(req.price(), req.discountedPrice());
        Ebook ebook = new Ebook();
        apply(ebook, req);
        ebook.setStatus(EbookStatus.DRAFT);
        ebook.setCreatedBy(actor);
        ebook.setUpdatedBy(actor);
        ebook = ebookRepository.save(ebook);
        auditService.record(actor, "EBOOK_CREATED", "Ebook", ebook.getId(),
                "Added ebook '" + ebook.getTitle() + "' (" + ebook.getEbookCode() + ") at " + ebook.effectivePrice(), ip);
        return toAdmin(ebook);
    }

    @Transactional
    public AdminEbook update(Long id, EbookRequest req, User actor, String ip) {
        Ebook ebook = requireEbook(id);
        if (!ebook.getEbookCode().equalsIgnoreCase(req.ebookCode()) && ebookRepository.existsByEbookCodeIgnoreCase(req.ebookCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "An ebook with this code already exists.");
        }
        validatePricing(req.price(), req.discountedPrice());
        BigDecimal oldPrice = ebook.effectivePrice();
        String oldTitle = ebook.getTitle();
        apply(ebook, req);
        ebook.setUpdatedBy(actor);
        ebook = ebookRepository.save(ebook);
        StringBuilder desc = new StringBuilder("Updated ebook '").append(ebook.getTitle()).append("'");
        if (!oldTitle.equals(ebook.getTitle())) {
            desc.append("; renamed from '").append(oldTitle).append("'");
        }
        if (oldPrice.compareTo(ebook.effectivePrice()) != 0) {
            desc.append("; price ").append(oldPrice).append(" -> ").append(ebook.effectivePrice());
            auditService.record(actor, "EBOOK_PRICE_CHANGED", "Ebook", id, "Effective price " + oldPrice + " -> " + ebook.effectivePrice(), ip);
        }
        auditService.record(actor, "EBOOK_UPDATED", "Ebook", id, desc.toString(), ip);
        return toAdmin(ebook);
    }

    @Transactional
    public AdminEbook changePrice(Long id, BigDecimal price, BigDecimal discountedPrice, User actor, String ip) {
        validatePricing(price, discountedPrice);
        Ebook ebook = requireEbook(id);
        BigDecimal old = ebook.effectivePrice();
        ebook.setPrice(price);
        ebook.setDiscountedPrice(discountedPrice);
        ebook.setUpdatedBy(actor);
        ebookRepository.save(ebook);
        auditService.record(actor, "EBOOK_PRICE_CHANGED", "Ebook", id, "Effective price " + old + " -> " + ebook.effectivePrice(), ip);
        return toAdmin(ebook);
    }

    @Transactional
    public AdminEbook setStatus(Long id, EbookStatus status, User actor, String ip) {
        Ebook ebook = requireEbook(id);
        if (status == EbookStatus.ACTIVE && ebook.getPdfPath() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upload the ebook PDF before activating it.");
        }
        EbookStatus old = ebook.getStatus();
        ebook.setStatus(status);
        ebook.setUpdatedBy(actor);
        ebookRepository.save(ebook);
        auditService.record(actor, status == EbookStatus.ACTIVE ? "EBOOK_ACTIVATED" : "EBOOK_DEACTIVATED", "Ebook", id,
                "'" + ebook.getTitle() + "': " + old + " -> " + status, ip);
        return toAdmin(ebook);
    }

    @Transactional
    public AdminEbook uploadCover(Long id, MultipartFile file, User actor, String ip) {
        Ebook ebook = requireEbook(id);
        String previous = ebook.getCoverImagePath();
        ebook.setCoverImagePath(storeCover(file));
        ebook.setUpdatedBy(actor);
        ebook = ebookRepository.save(ebook);
        if (previous != null && previous.startsWith("images/")) {
            storage.deleteQuietly(previous);
        }
        auditService.record(actor, "EBOOK_COVER_UPLOADED", "Ebook", id, "Cover " + (previous == null ? "uploaded" : "replaced") + " for '" + ebook.getTitle() + "'", ip);
        return toAdmin(ebook);
    }

    /** Stores / replaces the protected PDF. Existing purchasers immediately read the new file. */
    @Transactional
    public AdminEbook uploadPdf(Long id, MultipartFile file, User actor, String ip) {
        Ebook ebook = requireEbook(id);
        String previous = ebook.getPdfPath();
        ebook.setPdfPath(storePdf(file));
        ebook.setPdfOriginalName(FileStorageService.safeFilename(file.getOriginalFilename(), ebook.getEbookCode() + ".pdf"));
        ebook.setPdfSizeBytes(file.getSize());
        ebook.setUpdatedBy(actor);
        ebook = ebookRepository.save(ebook);
        if (previous != null) {
            storage.deleteQuietly(previous);
        }
        auditService.record(actor, previous == null ? "EBOOK_PDF_UPLOADED" : "EBOOK_PDF_REPLACED", "Ebook", id,
                "'" + ebook.getTitle() + "': " + ebook.getPdfOriginalName() + " (" + file.getSize() + " bytes)", ip);
        return toAdmin(ebook);
    }

    @Transactional
    public void reorder(List<Long> orderedIds, User actor, String ip) {
        List<Ebook> ebooks = ebookRepository.findAllById(orderedIds);
        for (Ebook ebook : ebooks) {
            ebook.setDisplayOrder(orderedIds.indexOf(ebook.getId()));
        }
        ebookRepository.saveAll(ebooks);
        auditService.record(actor, "EBOOK_REORDERED", "Ebooks reordered", ip);
    }

    /** Hard delete only when nobody owns it; purchased ebooks are deactivated instead so history stays intact. */
    @Transactional
    public void delete(Long id, User actor, String ip) {
        Ebook ebook = requireEbook(id);
        if (entitlementRepository.countByEbookId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This ebook has been purchased and cannot be deleted. Deactivate it instead — existing buyers keep access.");
        }
        ebookRepository.delete(ebook);
        storage.deleteQuietly(ebook.getPdfPath());
        if (ebook.getCoverImagePath() != null && ebook.getCoverImagePath().startsWith("images/")) {
            storage.deleteQuietly(ebook.getCoverImagePath());
        }
        auditService.record(actor, "EBOOK_DELETED", "Ebook", id, "Deleted ebook '" + ebook.getTitle() + "' (" + ebook.getEbookCode() + ")", ip);
    }

    // ---- Simple upload (Main Admin form: name, price, cover, PDF, optional info) -----------

    public static final String MSG_TITLE_REQUIRED = "Ebook name is required.";
    public static final String MSG_PRICE_REQUIRED = "Price is required.";
    public static final String MSG_BAD_PDF = "Please upload a valid PDF Ebook.";
    public static final String MSG_BAD_COVER = "Please upload a valid cover image.";
    public static final String MSG_DUPLICATE_TITLE = "An ebook with this name already exists.";

    /**
     * Creates an ebook in ONE step: validates everything first (name, price, both files —
     * content-sniffed, not just the extension), stores the cover and the PDF, then saves the
     * record as ACTIVE so it appears on the store immediately. If anything fails after a file
     * was written, that file is removed again; no half-created ebook is ever left behind.
     */
    @Transactional
    public AdminEbook createSimple(String title, BigDecimal price, String info, MultipartFile cover, MultipartFile pdf,
                                   User actor, String ip) {
        String cleanTitle = requireTitle(title);
        BigDecimal cleanPrice = requirePrice(price);
        String cleanInfo = requireInfo(info);
        if (ebookRepository.existsByTitleIgnoreCase(cleanTitle)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_DUPLICATE_TITLE);
        }
        requireFile(cover, FileStorageService.Kind.IMAGE, MSG_BAD_COVER);
        requireFile(pdf, FileStorageService.Kind.EBOOK, MSG_BAD_PDF);

        String coverPath = null;
        String pdfPath = null;
        try {
            coverPath = storeCover(cover);
            pdfPath = storePdf(pdf);
            Ebook ebook = new Ebook();
            ebook.setEbookCode(uniqueCodeFor(cleanTitle));
            ebook.setTitle(cleanTitle);
            ebook.setShortDescription(cleanInfo);
            ebook.setPrice(cleanPrice);
            ebook.setCoverImagePath(coverPath);
            ebook.setPdfPath(pdfPath);
            ebook.setPdfOriginalName(FileStorageService.safeFilename(pdf.getOriginalFilename(), ebook.getEbookCode() + ".pdf"));
            ebook.setPdfSizeBytes(pdf.getSize());
            ebook.setStatus(EbookStatus.ACTIVE);
            ebook.setCreatedBy(actor);
            ebook.setUpdatedBy(actor);
            ebook = ebookRepository.saveAndFlush(ebook);
            auditService.record(actor, "EBOOK_CREATED", "Ebook", ebook.getId(),
                    "Added ebook '" + ebook.getTitle() + "' (" + ebook.getEbookCode() + ") at " + ebook.effectivePrice() + " with PDF and cover; active", ip);
            return toAdmin(ebook);
        } catch (RuntimeException e) {
            storage.deleteQuietly(coverPath);
            storage.deleteQuietly(pdfPath);
            throw e;
        }
    }

    /** Edits name / price / info; a new cover or PDF replaces the old one, an absent file keeps it. */
    @Transactional
    public AdminEbook updateSimple(Long id, String title, BigDecimal price, String info, MultipartFile cover, MultipartFile pdf,
                                   User actor, String ip) {
        Ebook ebook = requireEbook(id);
        String cleanTitle = requireTitle(title);
        BigDecimal cleanPrice = requirePrice(price);
        String cleanInfo = requireInfo(info);
        if (ebookRepository.existsByTitleIgnoreCaseAndIdNot(cleanTitle, id)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_DUPLICATE_TITLE);
        }
        boolean newCover = cover != null && !cover.isEmpty();
        boolean newPdf = pdf != null && !pdf.isEmpty();
        if (newCover) requireFile(cover, FileStorageService.Kind.IMAGE, MSG_BAD_COVER);
        if (newPdf) requireFile(pdf, FileStorageService.Kind.EBOOK, MSG_BAD_PDF);

        String oldCover = ebook.getCoverImagePath();
        String oldPdf = ebook.getPdfPath();
        BigDecimal oldPrice = ebook.effectivePrice();
        String coverPath = null;
        String pdfPath = null;
        try {
            if (newCover) {
                coverPath = storeCover(cover);
                ebook.setCoverImagePath(coverPath);
            }
            if (newPdf) {
                pdfPath = storePdf(pdf);
                ebook.setPdfPath(pdfPath);
                ebook.setPdfOriginalName(FileStorageService.safeFilename(pdf.getOriginalFilename(), ebook.getEbookCode() + ".pdf"));
                ebook.setPdfSizeBytes(pdf.getSize());
            }
            ebook.setTitle(cleanTitle);
            ebook.setPrice(cleanPrice);
            if (ebook.getDiscountedPrice() != null && ebook.getDiscountedPrice().compareTo(cleanPrice) > 0) {
                ebook.setDiscountedPrice(null);   // a legacy discount can never exceed the new price
            }
            ebook.setShortDescription(cleanInfo);
            ebook.setUpdatedBy(actor);
            ebook = ebookRepository.saveAndFlush(ebook);
        } catch (RuntimeException e) {
            storage.deleteQuietly(coverPath);
            storage.deleteQuietly(pdfPath);
            throw e;
        }
        if (newCover && oldCover != null && oldCover.startsWith("images/")) storage.deleteQuietly(oldCover);
        if (newPdf && oldPdf != null) storage.deleteQuietly(oldPdf);
        if (oldPrice.compareTo(ebook.effectivePrice()) != 0) {
            auditService.record(actor, "EBOOK_PRICE_CHANGED", "Ebook", id, "Effective price " + oldPrice + " -> " + ebook.effectivePrice(), ip);
        }
        auditService.record(actor, "EBOOK_UPDATED", "Ebook", id, "Updated ebook '" + ebook.getTitle() + "'"
                + (newCover ? "; cover replaced" : "") + (newPdf ? "; PDF replaced (" + ebook.getPdfOriginalName() + ")" : ""), ip);
        return toAdmin(ebook);
    }

    private static String requireTitle(String title) {
        String t = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_TITLE_REQUIRED);
        }
        if (t.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ebook name is too long (maximum 200 characters).");
        }
        return t;
    }

    private static BigDecimal requirePrice(BigDecimal price) {
        if (price == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_PRICE_REQUIRED);
        }
        if (price.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Price cannot be negative.");
        }
        if (price.scale() > 2 || price.precision() - price.scale() > 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid price (up to 2 decimals).");
        }
        return price;
    }

    private static String requireInfo(String info) {
        String i = blankToNull(info);
        if (i != null && i.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Optional information is too long (maximum 500 characters).");
        }
        return i;
    }

    /** Type, signature and size must all be right; the size limit message is kept because it is actionable. */
    private void requireFile(MultipartFile file, FileStorageService.Kind kind, String friendly) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, friendly);
        }
        try {
            storage.validate(file, kind);
        } catch (ApiException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage().startsWith("File is too large") ? e.getMessage() : friendly);
        }
        if (!FileStorageService.looksLike(file, kind)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, friendly);
        }
    }

    private String storeCover(MultipartFile file) {
        requireFile(file, FileStorageService.Kind.IMAGE, MSG_BAD_COVER);
        return storage.store(file, FileStorageService.Kind.IMAGE);
    }

    private String storePdf(MultipartFile file) {
        requireFile(file, FileStorageService.Kind.EBOOK, MSG_BAD_PDF);
        return storage.store(file, FileStorageService.Kind.EBOOK);
    }

    /** Internal code derived from the name (the store shows the name); made unique with a numeric suffix. */
    private String uniqueCodeFor(String title) {
        String base = title.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "EBOOK";
        }
        if (base.length() > 32) {
            base = base.substring(0, 32).replaceAll("-+$", "");
        }
        String code = base;
        int n = 2;
        while (ebookRepository.existsByEbookCodeIgnoreCase(code)) {
            code = base + "-" + n++;
        }
        return code;
    }

    // ---- Mapping --------------------------------------------------------------------------

    private PublicEbook toPublic(Ebook e) {
        return new PublicEbook(e.getId(), e.getEbookCode(), e.getTitle(), e.getAuthor(), e.getShortDescription(),
                e.getDescription(), e.getCategory(), e.getLanguage(), e.getPrice(), e.getDiscountedPrice(), e.effectivePrice(),
            e.getCoverImagePath(), e.getPdfPath() != null, e.getPdfPath() != null);
    }

    public AdminEbook toAdmin(Ebook e) {
        return new AdminEbook(e.getId(), e.getEbookCode(), e.getTitle(), e.getAuthor(), e.getShortDescription(),
                e.getDescription(), e.getCategory(), e.getLanguage(), e.getPrice(), e.getDiscountedPrice(), e.effectivePrice(),
                e.getCoverImagePath(), e.getPdfPath() != null, e.getPdfOriginalName(), e.getPdfSizeBytes(), e.getStatus(),
                e.getDisplayOrder(), entitlementRepository.countByEbookId(e.getId()),
                entitlementRepository.countByEbookIdAndStatus(e.getId(), EntitlementStatus.ACTIVE),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static void apply(Ebook ebook, EbookRequest req) {
        ebook.setEbookCode(req.ebookCode().trim().toUpperCase());
        ebook.setTitle(req.title().trim());
        ebook.setAuthor(blankToNull(req.author()));
        ebook.setShortDescription(blankToNull(req.shortDescription()));
        ebook.setDescription(blankToNull(req.description()));
        ebook.setCategory(blankToNull(req.category()));
        ebook.setLanguage(blankToNull(req.language()));
        ebook.setPrice(req.price());
        ebook.setDiscountedPrice(req.discountedPrice());
        if (req.displayOrder() != null) {
            ebook.setDisplayOrder(req.displayOrder());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void validatePricing(BigDecimal price, BigDecimal discounted) {
        if (discounted != null && discounted.compareTo(price) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Discounted price cannot be higher than the list price.");
        }
    }
}
