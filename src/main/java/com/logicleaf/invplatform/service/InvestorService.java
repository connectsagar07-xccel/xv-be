package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.exception.ResourceNotFoundException;
import com.logicleaf.invplatform.model.Investor;
import com.logicleaf.invplatform.repository.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import com.logicleaf.invplatform.dto.InvestorStartupDTO;
import com.logicleaf.invplatform.model.Startup;
import com.logicleaf.invplatform.model.StartupInvestorMapping;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.repository.StartupInvestorMappingRepository;
import com.logicleaf.invplatform.repository.StartupRepository;
import com.logicleaf.invplatform.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class InvestorService {

    private final InvestorRepository investorRepository;
    private final StartupInvestorMappingRepository mappingRepository;
    private final UserRepository userRepository;
    private final StartupRepository startupRepository;

    /**
     * ✅ Create or update an investor
     */
    public Investor saveInvestor(Investor investor) {
        return investorRepository.save(investor);
    }

    /**
     * ✅ Fetch investor by ID
     */
    public Investor findById(String investorId) {
        return investorRepository.findById(investorId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found with id: " + investorId));
    }

    /**
     * ✅ Fetch investor linked to a specific user (userId)
     */
    public Investor findByUserId(String userId) {
        return investorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found for userId: " + userId));
    }

    /**
     * ✅ Fetch all investors
     */
    public List<Investor> getAllInvestors() {
        return investorRepository.findAll();
    }

    /**
     * ✅ Delete an investor by ID
     */
    public void deleteInvestor(String investorId) {
        Investor investor = findById(investorId);
        investorRepository.delete(investor);
    }

    /**
     * ✅ Get all connected startups for an investor
     */
    public List<InvestorStartupDTO> getConnectedStartups(String investorId) {
        List<StartupInvestorMapping> mappings = new ArrayList<>();

        // 1. Find Investor & User to get Email
        Investor investor = investorRepository.findById(investorId)
                .orElseThrow(() -> new ResourceNotFoundException("Investor not found"));

        User user = userRepository.findById(investor.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. By ID (active/pending)
        mappings.addAll(mappingRepository.findByInvestorId(investorId));

        // 3. By Email (invites)
        mappings.addAll(mappingRepository.findByInvestorEmail(user.getEmail()));

        // Deduplicate by mapping ID
        Map<String, StartupInvestorMapping> uniqueMappings = mappings.stream()
                .collect(Collectors.toMap(StartupInvestorMapping::getId, m -> m, (m1, m2) -> m1));

        return uniqueMappings.values().stream().map(m -> {
            Startup startup = startupRepository.findById(m.getStartupId()).orElse(null);
            if (startup == null)
                return null;

            return InvestorStartupDTO.builder()
                    .startupId(startup.getId())
                    .startupName(startup.getStartupName())
                    .sector(startup.getSector())
                    .stage(startup.getStage())
                    .fundingRaised(startup.getFundingRaised())
                    .hqLocation(startup.getHqLocation())
                    .teamSize(startup.getTeamSize())
                    .website(startup.getWebsite())
                    .valuation(startup.getValuation())
                    .mappingId(m.getId())
                    .status(m.getStatus())
                    .investorRole(m.getInvestorRole())
                    .build();
        })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
