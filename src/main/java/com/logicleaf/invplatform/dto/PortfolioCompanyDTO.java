package com.logicleaf.invplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioCompanyDTO {
    private String startupId;
    private String startupName;
    private String industry;
    private Integer teamSize;
    private String foundedYear; // Derived from createdAt or added to Startup if needed. Using createdAt year
                                // for now.
    private String stage;
    private String status; // Active, Exited, etc. Hardcoded to "Active" for now.

    private Double investmentAmount;
    private Double ownershipPercentage;

    private Double valuation;
    private Double mrr;
    private Double growthPercentage;

    private String lastUpdate;
}
