package com.lordsai.lsi.service;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import com.lordsai.lsi.exception.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws the course completion certificate: the ONE master Lord Sai design (the supplied certificate
 * with its student-specific text removed, classpath certificate/lord-sai-certificate-template.jpg)
 * as the full page, plus the dynamic student / course values in the exact positions, fonts, sizes and
 * colours measured on the supplied certificate. Nothing else is added or moved.
 *
 * <p>All positions below are pixels of the 2400 x 1600 master; the page is 12 x 8 in (864 x 576 pt),
 * i.e. the master at 200 dpi, so the aspect ratio is the original 3:2.
 */
@Service
public class CertificatePdfService {

    /** Everything printed that changes from one certificate to the next. */
    public record Content(String studentName, String studentId, String courseName, String coursePeriod,
                          String issueMonth, String verifyUrl) {
    }

    private static final float MASTER_W = 2400f, MASTER_H = 1600f;
    private static final float PAGE_W = 864f, PAGE_H = 576f;
    private static final float PX = PAGE_W / MASTER_W; // pt per master pixel

    private static final Color NAME_BLUE = new Color(2, 101, 152);    // "JOSTNA MHATRE" + underline
    private static final Color TEXT_BLUE = new Color(2, 101, 152);    // course paragraph
    private static final Color FOOTER_RED = new Color(114, 1, 24);    // "Student ID: ...  Issue : ..."
    private static final Color QR_BLUE = new Color(60, 64, 200);      // QR modules

    // Student name: Open Sans Bold, centred, underlined.
    private static final float NAME_CENTER_X = 1404f, NAME_BASELINE = 754.25f, NAME_SIZE = 74.4f, NAME_MAX_W = 1560f;
    private static final float UNDERLINE_BELOW = 4.75f, UNDERLINE_THICKNESS = 6f;
    // Course paragraph: Noto Serif regular / bold, justified, 5 lines in the original. A longer course
    // name keeps the full size while the text still ends above PARA_BOTTOM (15 px clear of the QR box
    // at y 1121 and the seal at ~1124); only beyond that does the text shrink, gently, to fit.
    private static final float PARA_LEFT = 557f, PARA_RIGHT = 2303f, PARA_FIRST_BASELINE = 845.75f;
    // Lines break at this width (measured from the original's line breaks) and are then justified
    // to the full PARA_LEFT..PARA_RIGHT measure, exactly like the supplied certificate.
    private static final float PARA_BREAK_WIDTH = 1725f;
    private static final float PARA_SIZE = 42.9f, PARA_PITCH = 53.25f;
    private static final float PARA_BOTTOM = 1106f, PARA_DESCENT = 0.25f;
    // "Student ID: ...  Issue : ..." — Open Sans Bold, centred under the seal / QR block.
    private static final float FOOTER_CENTER_X = 1346f, FOOTER_BASELINE = 1522.75f, FOOTER_SIZE = 44.4f, FOOTER_MAX_W = 1700f;
    // QR: the white square of the original (x 1661-1927, y 1121-1388) with its inner margin.
    private static final float QR_X = 1661f, QR_Y = 1121f, QR_SIZE = 267f, QR_INSET = 19f;

    private static final String BLOCK1_LEAD = "Successfully completed the professional course";
    private static final String BLOCK1_TAIL = " at Lord Sai Investment & Share Market Academy, Uran Navi Mumbai";
    private static final String BLOCK2_TAIL = "Gained strong knowledge in stock market basics, technical analysis, price action, risk management, "
            + "trading psychology, and live market strategies, demonstrating dedication and discipline throughout the program.";

    private final byte[] template;
    private final byte[] openSansBold;
    private final byte[] notoSerif;
    private final byte[] notoSerifBold;
    private final byte[] qrLogo;

    public CertificatePdfService() {
        this.template = resource("certificate/lord-sai-certificate-template.jpg");
        this.openSansBold = resource("certificate/fonts/OpenSans-Bold.ttf");
        this.notoSerif = resource("certificate/fonts/NotoSerif-Regular.ttf");
        this.notoSerifBold = resource("certificate/fonts/NotoSerif-Bold.ttf");
        this.qrLogo = resource("certificate/qr-logo.png");
    }

    public byte[] render(Content c) {
        require(c.studentName(), "student name");
        require(c.studentId(), "Student ID");
        require(c.courseName(), "course name");
        require(c.coursePeriod(), "course period");
        require(c.issueMonth(), "issue date");
        require(c.verifyUrl(), "verification link");
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            doc.addPage(page);
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Certificate of Completion");
            info.setAuthor("Lord Sai Investment & Share Market Academy");
            info.setSubject(clean(c.studentName()) + " — " + clean(c.courseName()));
            info.setCreationDate(Calendar.getInstance());

            PDImageXObject background = JPEGFactory.createFromByteArray(doc, template);
            PDType0Font sansBold = PDType0Font.load(doc, new ByteArrayInputStream(openSansBold), true);
            PDType0Font serif = PDType0Font.load(doc, new ByteArrayInputStream(notoSerif), true);
            PDType0Font serifBold = PDType0Font.load(doc, new ByteArrayInputStream(notoSerifBold), true);
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, qrLogo, "qr-logo.png");

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(background, 0, 0, PAGE_W, PAGE_H);
                drawName(cs, sansBold, clean(c.studentName()).toUpperCase(Locale.ROOT));
                drawParagraph(cs, serif, serifBold, clean(c.courseName()), clean(c.coursePeriod()));
                drawFooter(cs, sansBold, "Student ID: " + clean(c.studentId()) + "  Issue : " + clean(c.issueMonth()));
                drawQr(cs, c.verifyUrl().trim(), logo);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IllegalArgumentException e) {
            // A character none of the certificate fonts can print (e.g. a non-Latin script).
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The certificate cannot be generated: the student or course details contain characters the certificate fonts cannot print. "
                            + "Please correct the details and try again.");
        } catch (IOException e) {
            throw new UncheckedIOException("Certificate PDF could not be generated", e);
        }
    }

    // ---- student name -----------------------------------------------------------------------

    private void drawName(PDPageContentStream cs, PDType0Font font, String name) throws IOException {
        float size = NAME_SIZE * PX;
        float width = width(font, size, name);
        float max = NAME_MAX_W * PX;
        if (width > max) { // very long names shrink to fit the line instead of running into the margins
            size *= max / width;
            width = max;
        }
        float scale = size / (NAME_SIZE * PX);
        float x = NAME_CENTER_X * PX - width / 2f;
        float baseline = y(NAME_BASELINE);
        text(cs, font, size, NAME_BLUE, x, baseline, name);
        cs.setNonStrokingColor(NAME_BLUE);
        float thickness = UNDERLINE_THICKNESS * PX * scale;
        cs.addRect(x, baseline - (UNDERLINE_BELOW * PX * scale) - thickness, width, thickness);
        cs.fill();
    }

    // ---- course paragraph ---------------------------------------------------------------------

    private record Word(String text, boolean bold) {
    }

    private record Line(List<Word> words, boolean lastOfBlock) {
    }

    private void drawParagraph(PDPageContentStream cs, PDType0Font regular, PDType0Font bold,
                               String courseName, String period) throws IOException {
        List<List<Word>> blocks = new ArrayList<>();
        List<Word> first = new ArrayList<>(words(BLOCK1_LEAD, false));
        first.addAll(words("‘" + courseName + BLOCK1_TAIL, true));
        blocks.add(first);
        List<Word> second = new ArrayList<>(words("( " + period + " ) .", true));
        second.addAll(words(BLOCK2_TAIL, false));
        blocks.add(second);

        float maxWidth = (PARA_RIGHT - PARA_LEFT) * PX;
        float breakWidth = PARA_BREAK_WIDTH * PX;
        float size = PARA_SIZE * PX;
        List<Line> lines = layout(blocks, regular, bold, size, breakWidth);
        while (!fitsAboveSeal(lines.size(), size) && size > PARA_SIZE * PX * 0.6f) { // unusually long course names
            size *= 0.98f;
            lines = layout(blocks, regular, bold, size, breakWidth);
        }
        float pitch = PARA_PITCH * PX * (size / (PARA_SIZE * PX));
        float baseline = y(PARA_FIRST_BASELINE);
        float left = PARA_LEFT * PX;
        float space = width(regular, size, " ");
        for (Line line : lines) {
            float wordsWidth = 0;
            for (Word w : line.words()) {
                wordsWidth += width(w.bold() ? bold : regular, size, w.text());
            }
            int gaps = line.words().size() - 1;
            float gap = line.lastOfBlock() || gaps == 0 ? space : (maxWidth - wordsWidth) / gaps;
            float x = left;
            for (Word w : line.words()) {
                PDType0Font font = w.bold() ? bold : regular;
                text(cs, font, size, TEXT_BLUE, x, baseline, w.text());
                x += width(font, size, w.text()) + gap;
            }
            baseline -= pitch;
        }
    }

    /** Does a paragraph of {@code lines} lines at {@code sizePt} end above the seal / QR row? */
    private static boolean fitsAboveSeal(int lines, float sizePt) {
        float sizePx = sizePt / PX;
        float lastBaseline = PARA_FIRST_BASELINE + (lines - 1) * PARA_PITCH * (sizePx / PARA_SIZE);
        return lastBaseline + PARA_DESCENT * sizePx <= PARA_BOTTOM;
    }

    private static List<Line> layout(List<List<Word>> blocks, PDType0Font regular, PDType0Font bold,
                                     float size, float maxWidth) throws IOException {
        float space = width(regular, size, " ");
        List<Line> lines = new ArrayList<>();
        for (List<Word> block : blocks) {
            List<Word> current = new ArrayList<>();
            float currentWidth = 0;
            for (Word w : block) {
                float ww = width(w.bold() ? bold : regular, size, w.text());
                if (!current.isEmpty() && currentWidth + space + ww > maxWidth) {
                    lines.add(new Line(current, false));
                    current = new ArrayList<>();
                    currentWidth = 0;
                }
                currentWidth += (current.isEmpty() ? 0 : space) + ww;
                current.add(w);
            }
            if (!current.isEmpty()) {
                lines.add(new Line(current, true));
            }
        }
        return lines;
    }

    private static List<Word> words(String text, boolean bold) {
        List<Word> out = new ArrayList<>();
        for (String part : text.trim().split(" +")) {
            if (!part.isEmpty()) {
                out.add(new Word(part, bold));
            }
        }
        return out;
    }

    // ---- footer ------------------------------------------------------------------------------

    private void drawFooter(PDPageContentStream cs, PDType0Font font, String line) throws IOException {
        float size = FOOTER_SIZE * PX;
        float width = width(font, size, line);
        float max = FOOTER_MAX_W * PX;
        if (width > max) {
            size *= max / width;
            width = max;
        }
        text(cs, font, size, FOOTER_RED, FOOTER_CENTER_X * PX - width / 2f, y(FOOTER_BASELINE), line);
    }

    // ---- QR code -----------------------------------------------------------------------------

    /**
     * A unique QR per certificate, in the original's place and style: blue rounded modules, rounded
     * finder eyes and the Lord Sai logo in the centre (error correction H keeps it readable).
     */
    private void drawQr(PDPageContentStream cs, String url, PDImageXObject logo) throws IOException {
        ByteMatrix m;
        try {
            QRCode qr = Encoder.encode(url, ErrorCorrectionLevel.H, Map.of(EncodeHintType.CHARACTER_SET, "UTF-8"));
            m = qr.getMatrix();
        } catch (WriterException e) {
            throw new IOException("QR code could not be encoded", e);
        }
        int n = m.getWidth();
        float box = QR_SIZE * PX;
        float boxX = QR_X * PX;
        float boxY = y(QR_Y + QR_SIZE);
        cs.setNonStrokingColor(Color.WHITE);
        cs.addRect(boxX, boxY, box, box);
        cs.fill();

        float inset = QR_INSET * PX;
        float area = box - 2 * inset;
        float mod = area / n;
        float left = boxX + inset;
        float top = boxY + box - inset;

        // Logo zone in the middle: modules there are left out (error correction H recovers them).
        float logoW = area * 0.58f;
        float logoH = logoW * logo.getHeight() / logo.getWidth();
        float logoX = left + (area - logoW) / 2f;
        float logoY = top - (area + logoH) / 2f;
        float padX = logoX - mod * 0.6f, padY = logoY - mod * 0.6f, padW = logoW + mod * 1.2f, padH = logoH + mod * 1.2f;

        QrGrid grid = new QrGrid(m, n, left, top, mod, padX, padY, padW, padH);
        cs.setNonStrokingColor(QR_BLUE);
        float r = mod * 0.32f; // rounded squares, as on the supplied certificate's QR
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                if (!grid.dark(row, col)) {
                    continue;
                }
                boolean up = grid.dark(row - 1, col), down = grid.dark(row + 1, col);
                boolean lft = grid.dark(row, col - 1), rgt = grid.dark(row, col + 1);
                roundRect(cs, left + col * mod, top - (row + 1) * mod, mod, mod,
                        !up && !lft ? r : 0, !up && !rgt ? r : 0, !down && !rgt ? r : 0, !down && !lft ? r : 0);
            }
        }
        cs.fill();
        finder(cs, left, top, mod);
        finder(cs, left + (n - 7) * mod, top, mod);
        finder(cs, left, top - (n - 7) * mod, mod);

        cs.setNonStrokingColor(Color.WHITE);
        roundRect(cs, padX, padY, padW, padH, mod, mod, mod, mod);
        cs.fill();
        cs.drawImage(logo, logoX, logoY, logoW, logoH);
    }

    private record QrGrid(ByteMatrix m, int n, float left, float top, float mod,
                          float padX, float padY, float padW, float padH) {
        boolean finder(int row, int col) {
            return (row < 7 && col < 7) || (row < 7 && col >= n - 7) || (row >= n - 7 && col < 7);
        }

        boolean underLogo(int row, int col) {
            float cx = left + (col + 0.5f) * mod, cy = top - (row + 0.5f) * mod;
            return cx >= padX && cx <= padX + padW && cy >= padY && cy <= padY + padH;
        }

        boolean dark(int row, int col) {
            return row >= 0 && col >= 0 && row < n && col < n && m.get(col, row) == 1 && !finder(row, col) && !underLogo(row, col);
        }
    }

    /** A rounded finder eye: 7x7 ring and 3x3 centre, like the supplied certificate's QR. */
    private static void finder(PDPageContentStream cs, float x, float top, float mod) throws IOException {
        cs.setNonStrokingColor(QR_BLUE);
        roundRect(cs, x, top - 7 * mod, 7 * mod, 7 * mod, 2.2f * mod, 2.2f * mod, 2.2f * mod, 2.2f * mod);
        cs.fill();
        cs.setNonStrokingColor(Color.WHITE);
        roundRect(cs, x + mod, top - 6 * mod, 5 * mod, 5 * mod, 1.5f * mod, 1.5f * mod, 1.5f * mod, 1.5f * mod);
        cs.fill();
        cs.setNonStrokingColor(QR_BLUE);
        roundRect(cs, x + 2 * mod, top - 5 * mod, 3 * mod, 3 * mod, 1.1f * mod, 1.1f * mod, 1.1f * mod, 1.1f * mod);
        cs.fill();
    }

    /** Rectangle (x, y = bottom-left) with individual corner radii: top-left, top-right, bottom-right, bottom-left. */
    private static void roundRect(PDPageContentStream cs, float x, float y, float w, float h,
                                  float tl, float tr, float br, float bl) throws IOException {
        final float k = 0.5523f; // cubic Bezier approximation of a quarter circle
        cs.moveTo(x + bl, y);
        cs.lineTo(x + w - br, y);
        if (br > 0) cs.curveTo(x + w - br + br * k, y, x + w, y + br - br * k, x + w, y + br);
        cs.lineTo(x + w, y + h - tr);
        if (tr > 0) cs.curveTo(x + w, y + h - tr + tr * k, x + w - tr + tr * k, y + h, x + w - tr, y + h);
        cs.lineTo(x + tl, y + h);
        if (tl > 0) cs.curveTo(x + tl - tl * k, y + h, x, y + h - tl + tl * k, x, y + h - tl);
        cs.lineTo(x, y + bl);
        if (bl > 0) cs.curveTo(x, y + bl - bl * k, x + bl - bl * k, y, x + bl, y);
        cs.closePath();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static void text(PDPageContentStream cs, PDType0Font font, float size, Color color,
                             float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static float width(PDType0Font font, float size, String s) throws IOException {
        return font.getStringWidth(s) / 1000f * size;
    }

    /** Master pixel row -> PDF y (PDF measures from the bottom). */
    private static float y(float masterY) {
        return (MASTER_H - masterY) * PX;
    }

    /** Collapses whitespace and drops control characters so nothing odd reaches the page. */
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
    }

    private static void require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Certificate cannot be generated because required student/course information is missing: " + what + ".");
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Certificate resource missing from the application: " + path, e);
        }
    }
}
