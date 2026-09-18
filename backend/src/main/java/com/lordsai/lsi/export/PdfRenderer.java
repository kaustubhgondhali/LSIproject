package com.lordsai.lsi.export;

import com.lordsai.lsi.exception.ApiException;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a Thymeleaf template to a PDF on the server. One renderer serves every document —
 * invoices, report exports — so the branding (Lord Sai blue logo, fonts) lives in exactly one
 * place. Templates must be well-formed XHTML; the embedded Noto Sans font guarantees that the
 * Rupee sign and other Unicode text render identically on every server.
 */
@Component
public class PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderer.class);

    /** The Lord Sai BLUE logo (img/lord-sai-logo.png on the website), embedded as a data URI. */
    public static final String LOGO_RESOURCE = "/branding/lord-sai-logo.png";
    private static final String FONT_REGULAR = "/branding/fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD = "/branding/fonts/NotoSans-Bold.ttf";

    private final SpringTemplateEngine templateEngine;
    private volatile String cachedLogoDataUri;

    public PdfRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /** Renders the template with the model to XHTML (also usable as an inline "print" view). */
    public String html(String template, Map<String, Object> model) {
        Map<String, Object> vars = new HashMap<>(model);
        vars.putIfAbsent("logoDataUri", logoDataUri());
        Context context = new Context();
        context.setVariables(vars);
        return templateEngine.process(template, context);
    }

    /** Renders the template to PDF bytes. */
    public byte[] pdf(String template, Map<String, Object> model) {
        return pdfFromHtml(html(template, model));
    }

    public byte[] pdfFromHtml(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> resource(FONT_REGULAR), "Noto Sans", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> resource(FONT_BOLD), "Noto Sans", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[PDF] Rendering failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The PDF could not be generated.");
        }
    }

    public String logoDataUri() {
        String cached = cachedLogoDataUri;
        if (cached != null) {
            return cached;
        }
        try (InputStream in = resource(LOGO_RESOURCE)) {
            if (in == null) {
                return "";
            }
            cached = "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            cachedLogoDataUri = cached;
            return cached;
        } catch (IOException e) {
            return "";
        }
    }

    private InputStream resource(String path) {
        return PdfRenderer.class.getResourceAsStream(path);
    }
}
