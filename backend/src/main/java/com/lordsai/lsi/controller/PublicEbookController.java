package com.lordsai.lsi.controller;

import com.lordsai.lsi.dto.ApiResponse;
import com.lordsai.lsi.dto.ebook.EbookDtos.PublicEbook;
import com.lordsai.lsi.service.EbookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only ebook catalogue for visitors (store.html). Never exposes the PDF file. */
@RestController
@RequestMapping("/api/public/ebooks")
public class PublicEbookController {

    private final EbookService ebookService;

    public PublicEbookController(EbookService ebookService) {
        this.ebookService = ebookService;
    }

    @GetMapping
    public ApiResponse<List<PublicEbook>> list() {
        return ApiResponse.ok(ebookService.listPublic());
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicEbook> get(@PathVariable Long id) {
        return ApiResponse.ok(ebookService.getPublic(id));
    }
}
