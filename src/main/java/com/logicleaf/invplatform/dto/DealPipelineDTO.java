package com.logicleaf.invplatform.dto;

import com.logicleaf.invplatform.model.DealStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealPipelineDTO {
    private String id;
    private String startupId;
    private String investorId;
    private String startupName;
    private String industry;
    private String stage;
    private String valuation;
    private String funding;
    private DealStatus dealStatus;
    private String status;
    private String lastActivity;
}
