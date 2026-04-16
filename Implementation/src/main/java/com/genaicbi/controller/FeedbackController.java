package com.genaicbi.controller;

import com.genaicbi.dto.FeedbackRequest;
import com.genaicbi.model.FeedbackRecord;
import com.genaicbi.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public FeedbackRecord record(@Valid @RequestBody FeedbackRequest request) {
        return feedbackService.record(
                request.getCacheKey(),
                request.getVote(),
                request.getUserId(),
                request.getComment()
        );
    }

    @GetMapping
    public List<FeedbackRecord> list() {
        return feedbackService.list();
    }
}
