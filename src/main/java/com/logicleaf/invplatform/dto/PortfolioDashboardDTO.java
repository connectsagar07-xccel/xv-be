package com.logicleaf.invplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioDashboardDTO {
    private Integer totalCompanies;
    private Double totalInvested;
    private Double portfolioValue;
    private Double avgGrowth;
}
