package com.genaicbi.dto;

import java.util.List;

public record ChartPayload(
        String type,
        String title,
        List<String> labels,
        List<Double> values
) {
}
