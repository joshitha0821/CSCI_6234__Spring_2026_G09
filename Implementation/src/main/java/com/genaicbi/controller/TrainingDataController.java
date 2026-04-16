package com.genaicbi.controller;

import com.genaicbi.dto.TrainingExampleRequest;
import com.genaicbi.model.TrainingDocument;
import com.genaicbi.service.AuditService;
import com.genaicbi.service.TrainingDataService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/training")
public class TrainingDataController {

    private final TrainingDataService trainingDataService;
    private final AuditService auditService;

    public TrainingDataController(TrainingDataService trainingDataService, AuditService auditService) {
        this.trainingDataService = trainingDataService;
        this.auditService = auditService;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TrainingDocument uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String userId
    ) throws IOException {
        TrainingDocument doc = trainingDataService.addDocument(
                file.getOriginalFilename() == null ? "uploaded-document" : file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                userId
        );
        auditService.log(userId, "upload_training_doc", "ok", 0L, "docId=" + doc.id());
        return doc;
    }

    @GetMapping("/documents")
    public List<TrainingDocument> listDocuments() {
        return trainingDataService.listDocuments();
    }

    @PostMapping("/examples")
    public Map<String, String> addExample(@Valid @RequestBody TrainingExampleRequest request) {
        trainingDataService.addExample(request.getQuestion(), request.getSql());
        auditService.log("admin", "add_training_example", "ok", 0L, request.getQuestion());
        return Map.of("status", "ok");
    }

    @GetMapping("/examples")
    public List<String> listExamples() {
        return trainingDataService.listExamples();
    }
}
