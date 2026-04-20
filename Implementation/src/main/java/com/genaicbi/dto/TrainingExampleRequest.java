package com.genaicbi.dto;

import jakarta.validation.constraints.NotBlank;

public class TrainingExampleRequest {

    @NotBlank
    private String question;

    @NotBlank
    private String sql;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
