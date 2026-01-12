package com.logicleaf.invplatform.controller;

import com.logicleaf.invplatform.dto.DealPipelineRequestDTO;
import com.logicleaf.invplatform.model.Investor;
import com.logicleaf.invplatform.security.CustomUserDetails;
import com.logicleaf.invplatform.service.DealPipelineService;
import com.logicleaf.invplatform.service.InvestorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

import java.util.Map;

@RestController
@RequestMapping("/api/investor/deal-pipeline")
@RequiredArgsConstructor
@CrossOrigin("*")
@PreAuthorize("hasRole('INVESTOR')")
public class DealPipelineController {

    private final DealPipelineService dealPipelineService;
    private final InvestorService investorService;

    @GetMapping
    public ResponseEntity<?> getPipeline(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) com.logicleaf.invplatform.model.DealStatus filter) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Deal pipeline fetched successfully.");
        response.put("data", dealPipelineService.getPipelineForInvestor(investor.getId(), filter));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> addToPipeline(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody DealPipelineRequestDTO request) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        request.setInvestorId(investor.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Deal added to pipeline successfully.");
        response.put("data", dealPipelineService.addToPipeline(request));

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePipeline(@PathVariable String id,
            @RequestBody DealPipelineRequestDTO request) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Deal pipeline updated successfully.");
        response.put("data", dealPipelineService.updatePipeline(id, request));

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
        response.put("data", dealPipelineService.getDashboardMetrics(investor.getId()));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeFromPipeline(@PathVariable String id) {
        dealPipelineService.removeFromPipeline(id);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Deal removed from pipeline successfully.");

        return ResponseEntity.ok(response);
    }
}
