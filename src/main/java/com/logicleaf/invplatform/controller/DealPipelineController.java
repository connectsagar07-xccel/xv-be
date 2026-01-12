package com.logicleaf.invplatform.controller;

import com.logicleaf.invplatform.dto.DealPipelineDTO;
import com.logicleaf.invplatform.dto.DealPipelineDashboardDTO;
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

import java.util.List;

@RestController
@RequestMapping("/api/investor/deal-pipeline")
@RequiredArgsConstructor
@CrossOrigin("*")
@PreAuthorize("hasRole('INVESTOR')")
public class DealPipelineController {

    private final DealPipelineService dealPipelineService;
    private final InvestorService investorService;

    @GetMapping
    public ResponseEntity<List<DealPipelineDTO>> getPipeline(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) com.logicleaf.invplatform.model.DealStatus filter) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        return ResponseEntity.ok(dealPipelineService.getPipelineForInvestor(investor.getId(), filter));
    }

    @PostMapping
    public ResponseEntity<DealPipelineDTO> addToPipeline(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody DealPipelineRequestDTO request) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        request.setInvestorId(investor.getId());
        return ResponseEntity.ok(dealPipelineService.addToPipeline(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DealPipelineDTO> updatePipeline(@PathVariable String id,
            @RequestBody DealPipelineRequestDTO request) {
        return ResponseEntity.ok(dealPipelineService.updatePipeline(id, request));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DealPipelineDashboardDTO> getDashboardMetrics(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getUser().getId();
        Investor investor = investorService.findByUserId(userId);
        return ResponseEntity.ok(dealPipelineService.getDashboardMetrics(investor.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFromPipeline(@PathVariable String id) {
        dealPipelineService.removeFromPipeline(id);
        return ResponseEntity.noContent().build();
    }
}
