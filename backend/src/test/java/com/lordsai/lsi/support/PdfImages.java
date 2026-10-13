package com.lordsai.lsi.support;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

/** Test helper: what a generated PDF actually contains (images drawn on its pages, and its text). */
public final class PdfImages {

    private PdfImages() {
    }

    public static int count(byte[] pdf) throws IOException {
        int n = 0;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (PDPage page : doc.getPages()) {
                PDResources resources = page.getResources();
                for (COSName name : resources.getXObjectNames()) {
                    if (resources.getXObject(name) instanceof PDImageXObject) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    public static String text(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
