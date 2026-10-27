package com.lordsai.lsi.export;

import com.lordsai.lsi.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * A PDF as a picture, for places where only a picture can travel: WhatsApp's "click to chat"
 * cannot attach files, but a copied picture can be pasted into the chat. The picture is cropped to
 * what is printed, so a half-page receipt is not lost in an A4 sheet of white paper.
 */
public final class PdfPictures {

    private static final Logger log = LoggerFactory.getLogger(PdfPictures.class);

    /** Lighter than this (on every colour channel) counts as blank paper. */
    private static final int PAPER = 245;

    private PdfPictures() {
    }

    /**
     * Every page at the given resolution, one under the other (a one-page document is just that
     * page), cropped to the content plus a small white margin, as PNG bytes.
     */
    public static byte[] png(byte[] pdf, float dpi) {
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage all = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
            for (int i = 1; i < doc.getNumberOfPages(); i++) {
                all = below(all, renderer.renderImageWithDPI(i, dpi, ImageType.RGB));
            }
            ImageIO.write(cropToContent(all, Math.round(dpi / 6)), "png", out);   // margin ≈ 4 mm
            return out.toByteArray();
        } catch (Exception e) {
            log.error("[PDF] Picture rendering failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The picture could not be generated.");
        }
    }

    /** The smallest rectangle holding everything that is not blank paper, widened by the margin; a blank page stays whole. */
    static BufferedImage cropToContent(BufferedImage image, int margin) {
        int w = image.getWidth(), h = image.getHeight();
        int top = -1, bottom = -1, left = w, right = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                if (!paper(row[x])) {
                    if (top < 0) {
                        top = y;
                    }
                    bottom = y;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        if (top < 0) {
            return image;
        }
        int x0 = Math.max(0, left - margin), y0 = Math.max(0, top - margin);
        int x1 = Math.min(w, right + 1 + margin), y1 = Math.min(h, bottom + 1 + margin);
        BufferedImage cropped = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cropped.createGraphics();
        try {
            g.drawImage(image, -x0, -y0, null);
        } finally {
            g.dispose();
        }
        return cropped;
    }

    private static BufferedImage below(BufferedImage top, BufferedImage bottom) {
        BufferedImage both = new BufferedImage(Math.max(top.getWidth(), bottom.getWidth()), top.getHeight() + bottom.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = both.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, both.getWidth(), both.getHeight());
            g.drawImage(top, 0, 0, null);
            g.drawImage(bottom, 0, top.getHeight(), null);
        } finally {
            g.dispose();
        }
        return both;
    }

    private static boolean paper(int rgb) {
        return ((rgb >> 16) & 0xFF) >= PAPER && ((rgb >> 8) & 0xFF) >= PAPER && (rgb & 0xFF) >= PAPER;
    }
}
