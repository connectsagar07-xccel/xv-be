package com.logicleaf.invplatform.dto;

import com.logicleaf.invplatform.model.InvestorRole;
import com.logicleaf.invplatform.model.MappingStatus;
import com.logicleaf.invplatform.model.Sector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorStartupDTO {
    private String startupId;
    private String startupName;
    private Sector sector;
    private String stage;
    private Double fundingRaised;
    private String hqLocation;
    private Integer teamSize;
    private String website;
    private Double valuation;

    private String mappingId;
    private MappingStatus status;
    private InvestorRole investorRole;
}
