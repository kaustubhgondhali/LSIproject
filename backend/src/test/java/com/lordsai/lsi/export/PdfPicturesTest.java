package com.lordsai.lsi.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** A PDF page becomes a picture cropped to what is printed on it (the receipt picture pasted into WhatsApp). */
class PdfPicturesTest {

    @Test
    void cropsToTheContentPlusTheMargin() {
        BufferedImage page = white(200, 300);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(50, 60, 31, 31);   // x 50..80, y 60..90
        g.dispose();

        BufferedImage cropped = PdfPictures.cropToContent(page, 5);
        assertThat(cropped.getWidth()).isEqualTo(41);    // 45..85
        assertThat(cropped.getHeight()).isEqualTo(41);   // 55..95
        assertThat(cropped.getRGB(5, 5) & 0xFFFFFF).isZero();             // the square starts right after the margin
        assertThat(cropped.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF);  // the margin is white
    }

    @Test
    void aBlankPageStaysWholeAndContentAtTheEdgeIsNotCutOff() {
        BufferedImage blank = white(40, 60);
        assertThat(PdfPictures.cropToContent(blank, 5)).isSameAs(blank);

        BufferedImage edge = white(40, 60);
        edge.setRGB(0, 0, 0);
        BufferedImage cropped = PdfPictures.cropToContent(edge, 5);
        assertThat(cropped.getWidth()).isEqualTo(6);
        assertThat(cropped.getHeight()).isEqualTo(6);
    }

    @Test
    void aPdfPageBecomesACroppedPng() throws Exception {
        byte[] pdf = pdfWithRectangles(1);

        BufferedImage png = ImageIO.read(new ByteArrayInputStream(PdfPictures.png(pdf, 100f)));
        // 2 x 1 inch at 100 dpi, plus the 17 px (100 / 6) white margin on every side — not the whole A4 page.
        assertThat(png.getWidth()).isBetween(232, 236);
        assertThat(png.getHeight()).isBetween(132, 136);
    }

    @Test
    void aSecondPageIsNotCutOff() throws Exception {
        BufferedImage png = ImageIO.read(new ByteArrayInputStream(PdfPictures.png(pdfWithRectangles(2), 100f)));
        // Both rectangles, from the top of the first to the bottom of the second (one A4 page apart).
        double a4HeightPx = PDRectangle.A4.getHeight() / 72 * 100;
        assertThat(png.getWidth()).isBetween(232, 236);
        assertThat((double) png.getHeight()).isBetween(a4HeightPx + 132, a4HeightPx + 136);
    }

    /** Pages each with a black 2 x 1 inch rectangle in the same spot, in the upper half of the A4 page. */
    private static byte[] pdfWithRectangles(int pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setNonStrokingColor(Color.BLACK);
                    cs.addRect(72, 600, 144, 72);
                    cs.fill();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static BufferedImage white(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }
}
