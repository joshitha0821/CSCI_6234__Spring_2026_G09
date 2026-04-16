package com.genaicbi.controller;

import com.genaicbi.dto.AskQuestionRequest;
import com.genaicbi.dto.AskQuestionResponse;
import com.genaicbi.model.ChatHistoryEntry;
import com.genaicbi.service.ConversationService;
import com.genaicbi.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ask")
    public AskQuestionResponse ask(@Valid @RequestBody AskQuestionRequest request) {
        return chatService.ask(request.getUserId(), request.getQuestion());
    }

    @GetMapping("/history")
    public List<ChatHistoryEntry> history(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return chatService.history(userId, limit);
    }

    @GetMapping("/stats")
    public ConversationService.ConversationStats stats(@RequestParam(required = false) String userId) {
        return chatService.stats(userId);
    }

    @GetMapping("/suggestions")
    public List<String> suggestions(@RequestParam(defaultValue = "8") int limit) {
        return chatService.suggestionQuestions(limit);
    }

    @GetMapping("/export/{cacheKey}")
    public ResponseEntity<byte[]> export(@PathVariable String cacheKey) {
        String csv = chatService.exportCsv(cacheKey);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"answer-" + cacheKey + ".csv\"")
                .body(body);
    }
}
