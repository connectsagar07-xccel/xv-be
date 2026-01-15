package com.logicleaf.invplatform.controller;

import com.logicleaf.invplatform.dto.InvestorFullResponse;
import com.logicleaf.invplatform.dto.StartupActivityDTO;
import com.logicleaf.invplatform.model.DocumentType;
import com.logicleaf.invplatform.model.StartupActivity;
import com.logicleaf.invplatform.model.StartupDocument;
import com.logicleaf.invplatform.service.StartupActivityService;
import com.logicleaf.invplatform.service.StartupDocumentService;
import com.logicleaf.invplatform.service.StartupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/startup")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FOUNDER')")
public class StartupController {

    private final StartupService startupService;

    /**
     * Get complete investor info (User + Investor + Mapping) for logged-in
     * founder's startup.
     */
    @GetMapping("/investors")
    public ResponseEntity<?> getFullInvestors(@AuthenticationPrincipal UserDetails userDetails) {

        List<InvestorFullResponse> investors = startupService.getFullInvestorDataForStartup(userDetails.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Complete investor details fetched successfully.");
        response.put("data", investors);

        return ResponseEntity.ok(response);
    }

    private final StartupDocumentService documentService;

    /**
     * Upload a startup document (Financial / Legal / Pitch)
     */
    @PostMapping("/documents")
    public ResponseEntity<?> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType) {

        StartupDocument uploaded = documentService.uploadDocument(userDetails.getUsername(), file, documentType);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Document uploaded successfully.");
        response.put("data", uploaded);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<?> deleteStartupDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String documentId) {

        documentService.deleteDocument(documentId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Document deleted successfully.");
        return ResponseEntity.ok(response);
    }

    /**
     * Get all documents uploaded for this startup
     */
    @GetMapping("/documents")
    public ResponseEntity<?> getStartupDocuments(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "documentType", required = false) DocumentType documentType) {

        List<StartupDocument> documents = documentService.getDocuments(userDetails.getUsername(), documentType);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", documentType == null
                ? "All startup documents fetched successfully."
                : "Startup documents of type " + documentType + " fetched successfully.");
        response.put("data", documents);

        return ResponseEntity.ok(response);
    }

    private final StartupActivityService startupActivityService;
    // StartupRepository not needed for name anymore if stored in activity,
    // but check if it's used elsewhere. It was added just for the name in prev
    // step.

    @GetMapping("/{startupId}/latest-activity")
    public ResponseEntity<?> getStartupLatestActivity(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String startupId) {

        // Note: You might want to add connection/ownership checks here.
        // For now, assuming if they have the ID, they can see the public activity
        // status.

        StartupActivity activity = startupActivityService
                .getActivityByStartupId(startupId);

        StartupActivityDTO dto = null;
        if (activity != null) {
            dto = StartupActivityDTO.builder()
                    .startupId(activity.getStartupId())
                    .startupName(activity.getStartupName()) // Use stored name
                    .message(activity.getMessage())
                    .updatedAt(activity.getUpdatedAt())
                    .timeAgo(calculateTimeAgo(activity.getUpdatedAt()))
                    .build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", dto);

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
