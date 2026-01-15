package com.logicleaf.invplatform.controller;

import com.logicleaf.invplatform.dto.ConnectStartupRequest;
import com.logicleaf.invplatform.dto.InvestorStartupDTO;
import com.logicleaf.invplatform.dto.StartupActivityDTO;
import com.logicleaf.invplatform.model.Investor;
import com.logicleaf.invplatform.model.StartupActivity;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.service.ConnectionService;
import com.logicleaf.invplatform.service.InvestorService;
import com.logicleaf.invplatform.service.StartupActivityService;
import com.logicleaf.invplatform.service.UserService;
import com.logicleaf.invplatform.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        @Autowired
        private StartupActivityService startupActivityService;

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

        @GetMapping("/startups/latest-activities")
        public ResponseEntity<?> getLatestActivitiesForConnectedStartups(
                        @AuthenticationPrincipal UserDetails userDetails) {
                // 1. Verify Investor
                User user = userService.findByEmail(userDetails.getUsername())
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                Investor investor = investorService.findByUserId(user.getId());

                // 2. Get all connected startups
                List<InvestorStartupDTO> startups = investorService.getConnectedStartups(investor.getId());
                List<String> startupIds = startups.stream()
                                .map(InvestorStartupDTO::getStartupId)
                                .collect(Collectors.toList());

                // 3. Fetch activities
                List<StartupActivity> activities = startupActivityService.getActivitiesForStartups(startupIds);

                // Map startup IDs to names for easy lookup
                Map<String, String> startupNames = startups.stream()
                                .collect(Collectors.toMap(InvestorStartupDTO::getStartupId,
                                                InvestorStartupDTO::getStartupName));

                // 4. Convert to DTOs
                List<StartupActivityDTO> activityDTOs = activities.stream()
                                .map(activity -> {
                                        String name = startupNames.getOrDefault(activity.getStartupId(),
                                                        "Unknown Startup");
                                        return StartupActivityDTO.builder()
                                                        .startupId(activity.getStartupId())
                                                        .startupName(name)
                                                        .message(activity.getMessage())
                                                        .updatedAt(activity.getUpdatedAt())
                                                        .timeAgo(calculateTimeAgo(activity.getUpdatedAt()))
                                                        .build();
                                })
                                .collect(Collectors.toList());

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("data", activityDTOs);

                return ResponseEntity.ok(response);
        }

        private String calculateTimeAgo(java.time.Instant pastTime) {
                if (pastTime == null)
                        return "Unknown";
                long seconds = java.time.Duration.between(pastTime, java.time.Instant.now()).getSeconds();
                if (seconds < 60)
                        return "Just now";
                long minutes = seconds / 60;
                if (minutes < 60)
                        return minutes + " minutes ago";
                long hours = minutes / 60;
                if (hours < 24)
                        return hours + " hours ago";
                long days = hours / 24;
                if (days < 30)
                        return days + " days ago";
                long months = days / 30;
                if (months < 12)
                        return months + " months ago";
                return (months / 12) + " years ago";
        }
}