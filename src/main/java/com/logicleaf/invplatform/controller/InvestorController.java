package com.logicleaf.invplatform.controller;

import com.logicleaf.invplatform.dto.ConnectStartupRequest;
import com.logicleaf.invplatform.service.ConnectionService;
import jakarta.validation.Valid;

import com.logicleaf.invplatform.dto.InvestorStartupDTO;
import com.logicleaf.invplatform.exception.ResourceNotFoundException;
import com.logicleaf.invplatform.model.Investor;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.service.InvestorService;
import com.logicleaf.invplatform.service.UserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investor")
@PreAuthorize("hasRole('INVESTOR')")
public class InvestorController {

        @Autowired
        private ConnectionService connectionService;

        @Autowired
        private UserService userService;

        @Autowired
        private InvestorService investorService;

        @PostMapping("/connections/request")
        public ResponseEntity<?> requestConnection(@AuthenticationPrincipal UserDetails userDetails,
                        @Valid @RequestBody ConnectStartupRequest request) {
                connectionService.requestConnection(
                                userDetails.getUsername(),
                                request.getStartupId(),
                                request.getInvestorRole());
                return ResponseEntity.ok(Map.of(
                                "status", "success",
                                "message", "Connection request sent successfully."));
        }

        @GetMapping("/connections/{mappingId}/accept")
        public ResponseEntity<?> acceptInvitation(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable String mappingId) {
                connectionService.acceptInvitation(mappingId, userDetails.getUsername()); // pass email
                return ResponseEntity.ok(Map.of(
                                "status", "success",
                                "message", "Invitation accepted successfully."));
        }

        // PUBLIC: investor rejects founder invite (delete + email counterparty)
        @GetMapping("/connections/{mappingId}/reject")
        public ResponseEntity<?> rejectInvitation(@PathVariable String mappingId) {
                connectionService.rejectByInvestor(mappingId);
                return ResponseEntity.ok(Map.of(
                                "status", "success",
                                "message", "Invitation rejected and removed successfully."));
        }

        @GetMapping("/startups")
        public ResponseEntity<?> getConnectedStartups(@AuthenticationPrincipal UserDetails userDetails) {
                User user = userService.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                Investor investor = investorService.findByUserId(user.getId());

                List<InvestorStartupDTO> startups = investorService.getConnectedStartups(investor.getId());

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Connected startups fetched successfully.");
                response.put("data", startups);

                return ResponseEntity.ok(response);
        }
}