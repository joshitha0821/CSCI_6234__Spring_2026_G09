package com.genaicbi.service;

import com.genaicbi.model.TrainingDocument;
import com.genaicbi.util.TextUtil;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrainingDataService {

    private final ConcurrentHashMap<String, TrainingDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> exampleSqlByQuestion = new ConcurrentHashMap<>();

    public TrainingDocument addDocument(String fileName, String contentType, byte[] data, String uploadedBy) {
        String id = UUID.randomUUID().toString();
        String content = new String(data, StandardCharsets.UTF_8);
        TrainingDocument document = new TrainingDocument(
                id,
                fileName,
                contentType,
                content,
                uploadedBy == null || uploadedBy.isBlank() ? "admin" : uploadedBy,
                Instant.now()
        );
        documents.put(id, document);
        return document;
    }

    public List<TrainingDocument> listDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(TrainingDocument::uploadedAt).reversed())
                .toList();
    }

    public void addExample(String question, String sql) {
        exampleSqlByQuestion.put(TextUtil.normalize(question), sql.trim());
    }

    public List<String> listExamples() {
        List<String> examples = new ArrayList<>();
        exampleSqlByQuestion.forEach((question, sql) -> examples.add(question + " => " + sql));
        examples.sort(String::compareToIgnoreCase);
        return examples;
    }

    public List<String> listExampleQuestions(int limit) {
        int safeLimit = Math.max(1, limit);
        return exampleSqlByQuestion.keySet().stream()
                .sorted(String::compareToIgnoreCase)
                .limit(safeLimit)
                .toList();
    }

    public Optional<String> findSqlForQuestion(String question) {
        String normalized = TextUtil.normalize(question);
        String exact = exampleSqlByQuestion.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        for (var entry : exampleSqlByQuestion.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
