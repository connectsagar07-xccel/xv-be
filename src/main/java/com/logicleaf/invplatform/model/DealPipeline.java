package com.logicleaf.invplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "deal_pipelines")
public class DealPipeline {
    @Id
    private String id;
    private String startupId;
    private String investorId;
    private DealStatus status;
}
