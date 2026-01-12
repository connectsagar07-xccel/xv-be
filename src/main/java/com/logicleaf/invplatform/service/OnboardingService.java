package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.dto.FounderProfileRequest;
import com.logicleaf.invplatform.dto.InvestorProfileRequest;
import com.logicleaf.invplatform.exception.BadRequestException;
import com.logicleaf.invplatform.model.Investor;
import com.logicleaf.invplatform.model.Startup;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.repository.InvestorRepository;
import com.logicleaf.invplatform.repository.StartupRepository;
import com.logicleaf.invplatform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class OnboardingService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StartupRepository startupRepository;

    @Autowired
    private InvestorRepository investorRepository;

    public Startup createFounderProfile(String userEmail, FounderProfileRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        if (startupRepository.findByFounderUserId(user.getId()).isPresent()) {
            throw new BadRequestException("Founder profile already exists for this user.");
        }

        Startup startup = Startup.builder()
                .founderUserId(user.getId())
                .startupName(request.getStartupName())
                .sector(request.getSector())
                .stage(request.getStage())
                .fundingRaised(request.getFundingRaised())
                .hqLocation(request.getHqLocation())
                .teamSize(request.getTeamSize())
                .website(request.getWebsite())
                .valuation(request.getValuation())
                .build();

        Startup saved = startupRepository.save(startup);

        // 🔥 Mark onboarding as completed
        user.setOnboarded(true);
        userRepository.save(user);

        return saved;

    }

    public Investor createInvestorProfile(String userEmail, InvestorProfileRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        if (investorRepository.findByUserId(user.getId()).isPresent()) {
            throw new BadRequestException("Investor profile already exists for this user.");
        }

        Investor investor = Investor.builder()
                .userId(user.getId())
                .investorType(request.getInvestorType())
                .firmName(request.getFirmName())
                .ticketSize(request.getTicketSize())
                .sectorFocus(request.getSectorFocus())
                .aum(request.getAum())
                .build();

        Investor saved = investorRepository.save(investor);

        // 🔥 Mark onboarding as completed
        user.setOnboarded(true);
        userRepository.save(user);

        return saved;
    }
}