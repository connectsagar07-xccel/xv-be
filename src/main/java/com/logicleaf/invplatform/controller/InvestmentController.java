package com.logicleaf.invplatform.controller;

import com.logicleaf.invplatform.dto.InvestmentRequestDTO;
import com.logicleaf.invplatform.dto.PortfolioCompanyDTO;
import com.logicleaf.invplatform.dto.PortfolioDashboardDTO;
import com.logicleaf.invplatform.model.Investor;
import com.logicleaf.invplatform.security.CustomUserDetails;
import com.logicleaf.invplatform.service.InvestmentService;
import com.logicleaf.invplatform.service.InvestorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/investor/investments")
@RequiredArgsConstructor
@CrossOrigin("*")
@PreAuthorize("hasRole('INVESTOR')")
public class InvestmentController {

    private final InvestmentService investmentService;
    private final InvestorService investorService;

    @PostMapping
    public ResponseEntity<Void> addInvestment(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody InvestmentRequestDTO request) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        investmentService.addInvestment(investor.getId(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<PortfolioCompanyDTO>> getPortfolio(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        return ResponseEntity.ok(investmentService.getPortfolio(investor.getId()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<PortfolioDashboardDTO> getDashboardMetrics(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        return ResponseEntity.ok(investmentService.getDashboardMetrics(investor.getId()));
    }
}
