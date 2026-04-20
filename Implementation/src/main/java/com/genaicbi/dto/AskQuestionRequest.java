package com.genaicbi.dto;

import jakarta.validation.constraints.NotBlank;

public class AskQuestionRequest {

    @NotBlank
    private String question;

    private String userId;
    private String connectionId;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
}
