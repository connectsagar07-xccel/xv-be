package com.logicleaf.invplatform.dto;

import com.logicleaf.invplatform.model.Sector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FounderProfileRequest {

    @NotBlank
    private String startupName;

    @NotNull
    private Sector sector;

    @NotBlank
    private String stage;

    @NotNull
    private Double fundingRaised;

    @NotBlank
    private String hqLocation;

    @NotNull
    private Integer teamSize;

    private String website;

    private Double valuation;
}