package com.logicleaf.invplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentRequestDTO {
    private String startupId;
    private Double amount;
    private Double ownershipPercentage;
}
