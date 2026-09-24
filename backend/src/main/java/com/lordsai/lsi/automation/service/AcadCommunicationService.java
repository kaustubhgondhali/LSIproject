package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.AttachmentUploaded;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.BatchProgress;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.ChannelStatus;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.CommunicationLogResponse;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.Recipient;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.RecipientFilter;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendPreview;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendRequest;
import com.lordsai.lsi.automation.dto.AcadCommunicationDtos.SendResult;
import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.communication.WhatsAppMessageService;
import com.lordsai.lsi.email.MailSenderResolver;
import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.exception.ResourceNotFoundException;
import com.lordsai.lsi.service.AuditService;
import com.lordsai.lsi.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Email & WhatsApp automation of the Automation Admin: who can be reached (the academy's own
 * students in acad_students, filtered by batch / course / status), individual / selected /
 * filter-based sends over email, WhatsApp or both, confirmation previews, batched delivery,
 * retry of failures and the delivery history. Every message is an acad_communication_logs
 * row; nothing is ever reported as sent unless the provider accepted it.
 *
 * This service never reads website / LMS data: no users, enrollments, purchases or invoices.
 * SMTP and the WhatsApp provider are only the delivery mechanism.
 */
@Service
public class AcadCommunicationService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final int MAX_RECIPIENTS = 5000;

    private final AcadStudentRepository studentRepository;
    private final AcadCommunicationLogRepository logRepository;
    private final AcadCommunicationDispatcher dispatcher;
    private final AcadCommunicationSender sender;
    private final WhatsAppMessageService whatsApp;
    private final MailSenderResolver mailResolver;
    private final FileStorageService storage;
    private final AuditService auditService;
    private final boolean async;

    public AcadCommunicationService(AcadStudentRepository studentRepository,
                                    AcadCommunicationLogRepository logRepository,
                                    AcadCommunicationDispatcher dispatcher,
                                    AcadCommunicationSender sender,
                                    WhatsAppMessageService whatsApp,
                                    MailSenderResolver mailResolver,
                                    FileStorageService storage,
                                    AuditService auditService,
                                    @Value("${lsi.communication.async:true}") boolean async) {
        this.studentRepository = studentRepository;
        this.logRepository = logRepository;
        this.dispatcher = dispatcher;
        this.sender = sender;
        this.whatsApp = whatsApp;
        this.mailResolver = mailResolver;
        this.storage = storage;
        this.auditService = auditService;
        this.async = async;
    }

    // ---- channel status --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ChannelStatus channelStatus() {
        String source = mailResolver.currentSource();
        boolean email = !MailSenderResolver.SOURCE_NONE.equals(source);
        // WhatsApp is configured centrally by the Main Admin; the Automation Admin only sees connected / not.
        String waMessage = whatsApp.isConfigured()
                ? "WhatsApp is connected via " + whatsApp.providerName() + ". WhatsApp is managed from Main Admin → Settings → WhatsApp Settings."
                : WhatsAppMessageService.NOT_CONFIGURED_AUTOMATION;
        return new ChannelStatus(email, source, email ? "Email is configured (" + source + ")." : mailResolver.disabledReason(),
                whatsApp.isConfigured(), whatsApp.providerName(), waMessage, whatsApp.supportsDocuments());
    }

    // ---- recipients (Automation Admin students only) -----------------------------------------

    @Transactional(readOnly = true)
    public Page<Recipient> recipients(RecipientFilter filter, Pageable pageable) {
        List<Recipient> all = resolveByFilter(filter == null ? RecipientFilter.none() : filter);
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = (int) Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    /** Explicit ids win; otherwise the filter. Always de-duplicated by student. */
    @Transactional(readOnly = true)
    public List<Recipient> resolve(List<Long> ids, RecipientFilter filter) {
        if (ids != null && !ids.isEmpty()) {
            Set<Long> wanted = new HashSet<>(ids);
            return studentRepository.findAllById(wanted).stream()
                    .sorted((a, b) -> a.getStudentId().compareToIgnoreCase(b.getStudentId()))
                    .map(AcadCommunicationService::toRecipient).toList();
        }
        return resolveByFilter(filter == null ? RecipientFilter.none() : filter);
    }

    private List<Recipient> resolveByFilter(RecipientFilter f) {
        String status = f.status() == null || f.status().isBlank() ? AcadStudent.STATUS_ACTIVE
                : "ALL".equalsIgnoreCase(f.status()) ? null : f.status().trim().toUpperCase(Locale.ROOT);
        String q = f.q() == null || f.q().isBlank() ? null : AcadStudentService.normalizeName(f.q());
        List<AcadStudent> found = studentRepository.search(q, f.batchId(), f.courseId(), status, null,
                PageRequest.of(0, MAX_RECIPIENTS, Sort.by("studentId"))).getContent();
        return found.stream().map(AcadCommunicationService::toRecipient).toList();
    }

    private static Recipient toRecipient(AcadStudent s) {
        return new Recipient(s.getId(), s.getStudentId(), s.getFullName(), s.getEmail(), s.getMobile(),
                s.getBatch() == null ? null : s.getBatch().getName(),
                s.getCourse() == null ? null : s.getCourse().getName(), s.getStatus());
    }

    /** What the confirmation dialog shows: "You are about to send this message to X students." */
    @Transactional(readOnly = true)
    public SendPreview preview(SendRequest req) {
        List<Recipient> recipients = resolve(req.studentIds(), req.filter());
        boolean email = req.channels().contains(CommunicationChannel.EMAIL);
        boolean wa = req.channels().contains(CommunicationChannel.WHATSAPP);
        int emailCount = email ? (int) recipients.stream().filter(r -> r.email() != null && !r.email().isBlank()).count() : 0;
        int waCount = wa ? (int) recipients.stream().filter(r -> WhatsAppMessageService.toE164(r.mobile()) != null).count() : 0;
        return new SendPreview(recipients.size(), emailCount, waCount, whatsApp.isConfigured(), whatsApp.statusMessage(),
                recipients.stream().limit(10).toList());
    }

    // ---- sending ---------------------------------------------------------------------------

    /**
     * Queues one row per (student, channel) — never two for the same student even if several
     * filters match — then hands the batch to the dispatcher. Individual sends (one recipient)
     * are processed immediately so the office sees the result at once.
     */
    @Transactional
    public SendResult send(SendRequest req, User actor, String ip) {
        List<Recipient> recipients = resolve(req.studentIds(), req.filter());
        if (recipients.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No students match the selection.");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A single send is limited to " + MAX_RECIPIENTS + " students. Narrow the filter.");
        }
        if (req.channels().contains(CommunicationChannel.EMAIL) && (req.subject() == null || req.subject().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A subject is required for email.");
        }
        String attachmentPath = validateAttachment(req.attachmentPath());
        String attachmentName = attachmentPath == null ? null
                : FileStorageService.safeFilename(req.attachmentName(), attachmentPath.substring(attachmentPath.lastIndexOf('/') + 1));

        String batchRef = "AUTO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        CommunicationType type = recipients.size() == 1 ? CommunicationType.ADMIN_INDIVIDUAL : CommunicationType.ADMIN_BULK;
        Set<String> seen = new HashSet<>();
        int emailQueued = 0;
        int waQueued = 0;
        int skipped = 0;
        for (Recipient r : recipients) {
            for (CommunicationChannel channel : req.channels()) {
                String to = channel == CommunicationChannel.EMAIL ? r.email() : r.mobile();
                if (to == null || to.isBlank() || (channel == CommunicationChannel.WHATSAPP && WhatsAppMessageService.toE164(to) == null)) {
                    skipped++;
                    continue;
                }
                if (!seen.add(r.id() + ":" + channel)) {
                    continue;
                }
                AcadCommunicationLog row = new AcadCommunicationLog();
                row.setChannel(channel);
                row.setMessageType(type);
                row.setStudent(studentRepository.getReferenceById(r.id()));
                row.setStudentCode(r.studentId());
                row.setStudentName(r.fullName());
                row.setRecipient(to.trim());
                row.setSubject(req.subject() == null ? null : req.subject().trim());
                row.setBody(req.message().trim());
                row.setStatus(CommunicationStatus.PENDING);
                row.setAttachmentPath(attachmentPath);
                row.setAttachmentName(attachmentName);
                row.setBatchRef(batchRef);
                row.setSentBy(actor);
                logRepository.save(row);
                if (channel == CommunicationChannel.EMAIL) emailQueued++; else waQueued++;
            }
        }
        int queued = emailQueued + waQueued;
        auditService.record(actor, type == CommunicationType.ADMIN_BULK ? "AUTOMATION_BULK_QUEUED" : "AUTOMATION_MESSAGE_QUEUED",
                "AcadCommunicationBatch", null, batchRef + ": " + recipients.size() + " student(s), " + emailQueued + " email, "
                        + waQueued + " WhatsApp, channels " + req.channels() + (skipped > 0 ? ", skipped " + skipped + " (no address)" : ""), ip);
        if (queued == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "None of the selected students has a usable address for the chosen channel(s).");
        }

        if (async && recipients.size() > 1) {
            // Runs after this transaction commits so the rows are visible to the dispatcher.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatchAsync(batchRef);
                }
            });
            return new SendResult(batchRef, recipients.size(), queued, emailQueued, waQueued, skipped,
                    "Queued " + queued + " message(s) for " + recipients.size() + " student(s). Delivery runs in batches; check Message History for progress.");
        }
        // Individual send (or async disabled): deliver now, in this request.
        List<AcadCommunicationLog> rows = logRepository.findByBatchRefOrderByIdAsc(batchRef);
        logRepository.flush();
        int sent = 0;
        List<String> failures = new ArrayList<>();
        for (AcadCommunicationLog row : rows) {
            AcadCommunicationLog result = sender.sendOne(row.getId());
            if (result.getStatus() == CommunicationStatus.SENT) sent++;
            else failures.add(result.getChannel() + ": " + result.getErrorReason());
        }
        String message = sent == queued ? "Sent " + sent + " message(s)."
                : failures.isEmpty() ? "Sent " + sent + " of " + queued + " message(s)."
                : "Sent " + sent + " of " + queued + " message(s). " + String.join(" ", failures);
        return new SendResult(batchRef, recipients.size(), queued, emailQueued, waQueued, skipped, message);
    }

    private String validateAttachment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String p = path.trim();
        if (!p.startsWith("attachments/") || p.contains("..") || !storage.exists(p)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The attachment was not found. Upload it again.");
        }
        return p;
    }

    /** Stores an office-chosen attachment (PDF / image) for a message. Validated like every upload. */
    public AttachmentUploaded uploadAttachment(MultipartFile file) {
        String path = storage.store(file, FileStorageService.Kind.ATTACHMENT);
        return new AttachmentUploaded(path, FileStorageService.safeFilename(file.getOriginalFilename(), "attachment"), file.getSize());
    }

    @Transactional(readOnly = true)
    public BatchProgress progress(String batchRef) {
        long total = logRepository.countByBatchRef(batchRef);
        if (total == 0) {
            throw ResourceNotFoundException.of("Automation batch", batchRef);
        }
        return new BatchProgress(batchRef, total,
                logRepository.countByBatchRefAndStatus(batchRef, CommunicationStatus.PENDING),
                logRepository.countByBatchRefAndStatus(batchRef, CommunicationStatus.SENT),
                logRepository.countByBatchRefAndStatus(batchRef, CommunicationStatus.FAILED));
    }

    // ---- history & retry -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<CommunicationLogResponse> history(CommunicationChannel channel, CommunicationStatus status, Long studentId,
                                                  LocalDate from, LocalDate to, String q, Pageable pageable) {
        Instant f = from == null ? null : from.atStartOfDay(INDIA).toInstant();
        Instant t = to == null ? null : to.plusDays(1).atStartOfDay(INDIA).toInstant();
        String term = q == null || q.isBlank() ? null : q.trim();
        return logRepository.search(channel, status, studentId, f, t, term, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CommunicationLogResponse> historyForStudent(Long acadStudentId) {
        return logRepository.findByStudentIdOrderByCreatedAtDesc(acadStudentId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CommunicationLogResponse get(Long id) {
        return toResponse(logRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Automation message", id)));
    }

    /** Re-sends one FAILED (or stuck PENDING) message right away and returns the new outcome. */
    @Transactional
    public CommunicationLogResponse retry(Long id, User actor, String ip) {
        AcadCommunicationLog row = logRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Automation message", id));
        if (row.getStatus() == CommunicationStatus.SENT) {
            throw new ApiException(HttpStatus.CONFLICT, "This message was already delivered.");
        }
        auditService.record(actor, "AUTOMATION_MESSAGE_RETRY", "AcadCommunicationLog", id,
                row.getChannel() + " to " + row.getRecipient() + " (attempt " + (row.getAttempts() + 1) + ")", ip);
        logRepository.flush();
        return toResponse(sender.sendOne(id));
    }

    public CommunicationLogResponse toResponse(AcadCommunicationLog c) {
        return new CommunicationLogResponse(c.getId(), c.getCreatedAt(), c.getChannel(), c.getMessageType(),
                c.getStudent() == null ? null : c.getStudent().getId(), c.getStudentCode(), c.getStudentName(), c.getRecipient(),
                c.getSubject(), c.getBody(), c.getStatus(), c.getProvider(), c.getProviderMessageId(), c.getErrorReason(),
                c.getAttachmentName(), c.getBatchRef(), c.getAttempts(), c.getLastAttemptAt(), c.getSentAt(),
                c.getSentBy() == null ? "System" : c.getSentBy().getFullName());
    }

    /** Summary counters for the Automation Admin dashboard. */
    @Transactional(readOnly = true)
    public Map<String, Long> counters() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("pending", logRepository.countByStatus(CommunicationStatus.PENDING));
        m.put("sent", logRepository.countByStatus(CommunicationStatus.SENT));
        m.put("failed", logRepository.countByStatus(CommunicationStatus.FAILED));
        m.put("emailSent", logRepository.countByChannelAndStatus(CommunicationChannel.EMAIL, CommunicationStatus.SENT));
        m.put("whatsappSent", logRepository.countByChannelAndStatus(CommunicationChannel.WHATSAPP, CommunicationStatus.SENT));
        return m;
    }
}
