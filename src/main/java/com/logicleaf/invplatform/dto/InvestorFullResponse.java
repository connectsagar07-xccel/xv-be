package com.logicleaf.invplatform.dto;

import com.logicleaf.invplatform.model.InvestorType;
import com.logicleaf.invplatform.model.MappingStatus;
import com.logicleaf.invplatform.model.Sector;
import com.logicleaf.invplatform.model.InvestorRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Full investor information including user, investor profile, and mapping
 * details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestorFullResponse {

    // 🧑 User Info
    private String userId;
    private String name;
    private String email;
    private String phone;

    // 💼 Investor Profile
    private String investorId;
    private String firmName;
    private InvestorType investorType;
    private String ticketSize;
    private List<Sector> sectorFocus;
    private Double aum;

    // 🔗 Mapping Details
    private String mappingId;
    private InvestorRole investorRole;
    private MappingStatus status;

    // 💰 Investment Details
    private Double ownershipPercentage;
    private Double totalInvestedAmount;
    private String investedAt;
}
