package com.xiaosu.knowledge;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadService documentUploadService;

    public DocumentController(DocumentUploadService documentUploadService) {
        this.documentUploadService = documentUploadService;
    }

    @PostMapping
    public ResponseEntity<DocumentUploadService.UploadResult> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(documentUploadService.upload(file));
    }

    @GetMapping
    public List<DocumentUploadService.DocumentSummary> list() {
        return documentUploadService.listDocuments();
    }
}
