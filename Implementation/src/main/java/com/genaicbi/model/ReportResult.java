package com.genaicbi.model;

import com.genaicbi.dto.ChartPayload;

public record ReportResult(
        ChartPayload chart,
        String summary
) {
}
