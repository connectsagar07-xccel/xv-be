package com.logicleaf.invplatform.dto;

import com.logicleaf.invplatform.model.Report;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PortfolioStartupSummary {
    private String startupId;
    private String startupName;
    private Map<String, String> latestMetrics; // e.g., { "Revenue": "10000", "Burn": "5000" }
    private List<Report> recentReports;
}