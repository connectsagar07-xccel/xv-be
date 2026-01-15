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
    // Startup Data
    private String startupId;
    private String startupName;
    private String startupIndustry;
    private Integer startupTeamSize;
    private String startupFoundedYear;
    private Double startupMrr;
    private Double startupGrowthPercentage;

    // Investment Data
    private Double investmentAmount;
    private Double investmentOwnershipPercentage;
    private String investmentCurrency;
    private String investmentStage;
    private java.time.LocalDate investmentDate;
    private Double valuationAtInvestment;
    private String investmentNotes;
    private String investmentLastUpdate;
}
