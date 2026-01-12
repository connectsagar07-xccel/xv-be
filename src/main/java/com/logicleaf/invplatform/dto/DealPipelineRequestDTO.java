package com.logicleaf.invplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealPipelineRequestDTO {
    private String startupId;
    private String investorId;
    private com.logicleaf.invplatform.model.DealStatus status;
}
