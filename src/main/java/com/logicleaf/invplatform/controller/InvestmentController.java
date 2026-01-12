package com.logicleaf.invplatform.controller;

import com.logicleaf.invplatform.dto.InvestmentRequestDTO;
import com.logicleaf.invplatform.model.Investor;
import com.logicleaf.invplatform.security.CustomUserDetails;
import com.logicleaf.invplatform.service.InvestmentService;
import com.logicleaf.invplatform.service.InvestorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/investor/investments")
@RequiredArgsConstructor
@CrossOrigin("*")
@PreAuthorize("hasRole('INVESTOR')")
public class InvestmentController {

    private final InvestmentService investmentService;
    private final InvestorService investorService;

    @PostMapping
    public ResponseEntity<?> addInvestment(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody InvestmentRequestDTO request) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        investmentService.addInvestment(investor.getId(), request);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Investment added successfully.");

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getPortfolio(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Portfolio fetched successfully.");
        response.put("data", investmentService.getPortfolio(investor.getId()));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardMetrics(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Dashboard metrics fetched successfully.");
        response.put("data", investmentService.getDashboardMetrics(investor.getId()));

        return ResponseEntity.ok(response);
    }
}
