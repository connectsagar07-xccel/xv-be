package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.exception.BadRequestException;
import com.logicleaf.invplatform.exception.ResourceNotFoundException;

import com.logicleaf.invplatform.model.*;
import com.logicleaf.invplatform.repository.*;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimelyReportService {

    private final TimelyReportRepository timelyReportRepository;
    private final UserRepository userRepository;
    private final StartupRepository startupRepository;
    private final MailService mailService;
    private final InvestorService investorService;

    @Autowired
    private PdfGeneratorService pdfGeneratorService;

    @Autowired
    private FileStorageService fileStorageService;

    public TimelyReport createTimelyReport(TimelyReport report, MultipartFile[] attachments) {
        if (report.getTitle() == null || report.getTitle().isBlank()) {
            throw new BadRequestException("Report title cannot be empty");
        }

        // Validate founder & startup
        User founder = userRepository.findById(report.getFounderUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Founder not found"));
        Startup startup = startupRepository.findByFounderUserId(founder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found for founder"));

        if (report.isDraftReport()) {
            boolean draftExists = timelyReportRepository.existsByStartupIdAndDraftReportTrue(startup.getId());
            if (draftExists) {
                throw new BadRequestException(
                        "A draft report already exists. Please update or delete it before creating a new one.");
            }
        }

        // 💾 Save all uploaded files
        if (attachments != null && attachments.length > 0) {
            List<TimelyReportAttachment> savedFiles = saveAttachments(attachments);
            report.setAttachments(savedFiles);
        }

        report.setStartupId(startup.getId());
        report.setCreatedAt(System.currentTimeMillis());
        report.setUpdatedAt(System.currentTimeMillis());

        // ✅ Generate PDF report
        byte[] pdfBytes = pdfGeneratorService.generateTimelyReportPdf(report, startup.getStartupName());

        TimelyReportAttachment pdfAttachment = savePdfAttachment(pdfBytes, report.getTitle(), startup.getStartupName());
        report.setReportPdf(pdfAttachment);

        TimelyReport savedReport = timelyReportRepository.save(report);

        // ✅ Send to investors
        if (!report.isDraftReport()) {
            if (report.getInvestorUserIds() != null && !report.getInvestorUserIds().isEmpty()) {
                for (String investorId : report.getInvestorUserIds()) {
                    try {
                        Investor investor = investorService.findById(investorId);
                        User investorUser = userRepository.findById(investor.getUserId())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "User not found for investor: " + investorId));

                        mailService.sendTimelyReportWithPdf(
                                founder.getEmail(),
                                investorUser.getEmail(), // ✅ actual email from user
                                startup.getStartupName(),
                                savedReport,
                                pdfBytes,
                                pdfAttachment.getFileName());
                    } catch (Exception e) {
                        System.err.println("⚠️ Failed to send email to investor " + investorId + ": " + e.getMessage());
                    }
                }
            }
        }

        return savedReport;
    }

    // 💾 Save PDF file to disk and return reference
    private TimelyReportAttachment savePdfAttachment(byte[] pdfBytes, String title, String startupName) {
        try {

            String fileName = startupName + "_" + title + ".pdf";
            String safeFileName = fileStorageService.getUniqueFileName(fileName);

            // Use global file storage service to save file
            String savedPath = fileStorageService.uploadFile(safeFileName, pdfBytes);

            return TimelyReportAttachment.builder()
                    .fileName(safeFileName)
                    .filePath(savedPath)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to save generated PDF report: " + e.getMessage(), e);
        }
    }

    /**
     * Save all files to the global folder and return a list of Attachment objects.
     */
    private List<TimelyReportAttachment> saveAttachments(MultipartFile[] attachments) {
        List<TimelyReportAttachment> savedFiles = new ArrayList<>();

        for (MultipartFile file : attachments) {
            try {
                String fileName = (file.getOriginalFilename() != null)
                        ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9_.\\-]", "_")
                        : "attachment";

                String safeFileName = fileStorageService.getUniqueFileName(fileName);
                // Save using FileStorageService
                String savedPath = fileStorageService.uploadFile(safeFileName, file.getBytes());

                savedFiles.add(TimelyReportAttachment.builder()
                        .fileName(safeFileName)
                        .filePath(savedPath)
                        .build());

            } catch (IOException e) {
                throw new RuntimeException("Failed to save attachment: " + e.getMessage(), e);
            }
        }

        return savedFiles;
    }

    public TimelyReport updateTimelyReport(String reportId, TimelyReport updatedReport, MultipartFile[] attachments) {
        // Fetch existing report
        TimelyReport existing = timelyReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Timely report not found: " + reportId));

        // Verify founder (security check)
        if (!existing.getFounderUserId().equals(updatedReport.getFounderUserId())) {
            throw new BadRequestException("You are not authorized to update this report.");
        }

        // Fetch founder and startup
        User founder = userRepository.findById(updatedReport.getFounderUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Founder not found"));
        Startup startup = startupRepository.findByFounderUserId(founder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found for founder"));

        // Delete previous attachments (if any)
        if (existing.getAttachments() != null && !existing.getAttachments().isEmpty()) {
            for (TimelyReportAttachment oldFile : existing.getAttachments()) {
                try {
                    fileStorageService.deleteFile(oldFile.getFilePath());
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to delete old attachment: " + oldFile.getFilePath());
                }
            }
        }

        // Delete old generated PDF (if any)
        if (existing.getReportPdf() != null) {
            try {
                fileStorageService.deleteFile(existing.getReportPdf().getFilePath());
            } catch (Exception e) {
                System.err.println("⚠️ Failed to delete old PDF: " + e.getMessage());
            }
        }

        // Clear old attachments from DB reference
        existing.setAttachments(null);
        existing.setReportPdf(null);

        // Upload new attachments
        List<TimelyReportAttachment> newFiles = new ArrayList<>();
        if (attachments != null && attachments.length > 0) {
            newFiles = saveAttachments(attachments);
        }
        existing.setAttachments(newFiles);

        // Update report data fields
        existing.setTitle(updatedReport.getTitle());
        existing.setReportingPeriod(updatedReport.getReportingPeriod());
        existing.setKeyMetrics(updatedReport.getKeyMetrics());
        existing.setMonthlyRevenue(updatedReport.getMonthlyRevenue());
        existing.setMonthlyBurn(updatedReport.getMonthlyBurn());
        existing.setCashRunway(updatedReport.getCashRunway());
        existing.setTeamSize(updatedReport.getTeamSize());
        existing.setKeyAchievements(updatedReport.getKeyAchievements());
        existing.setChallengesAndLearnings(updatedReport.getChallengesAndLearnings());
        existing.setOtherKeyMetrics(updatedReport.getOtherKeyMetrics());
        existing.setAsksFromInvestors(updatedReport.getAsksFromInvestors());
        existing.setDraftReport(updatedReport.isDraftReport());
        existing.setInvestorUserIds(updatedReport.getInvestorUserIds());
        existing.setUpdatedAt(System.currentTimeMillis());

        TimelyReport savedReport = timelyReportRepository.save(existing);

        byte[] pdfBytes = pdfGeneratorService.generateTimelyReportPdf(savedReport, startup.getStartupName());
        TimelyReportAttachment pdfAttachment = savePdfAttachment(pdfBytes, savedReport.getTitle(),
                startup.getStartupName());
        savedReport.setReportPdf(pdfAttachment);
        timelyReportRepository.save(savedReport);

        // ✅ Optional: send updated PDF to investors (if not draft)
        if (!savedReport.isDraftReport() && savedReport.getInvestorUserIds() != null) {
            for (String investorId : savedReport.getInvestorUserIds()) {
                try {
                    Investor investor = investorService.findById(investorId);
                    User investorUser = userRepository.findById(investor.getUserId())
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("User not found for investor: " + investorId));

                    mailService.sendTimelyReportWithPdf(
                            founder.getEmail(),
                            investorUser.getEmail(),
                            startup.getStartupName(),
                            savedReport,
                            pdfBytes,
                            pdfAttachment.getFileName());
                } catch (Exception e) {
                    System.err.println(
                            "⚠️ Failed to send updated report email to investor " + investorId + ": " + e.getMessage());
                }
            }
        }

        return savedReport;
    }

    public TimelyReport getDraftTimelyReport(String founderUserId) {
        Startup startup = startupRepository.findByFounderUserId(founderUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found for founder: " + founderUserId));

        TimelyReport draft = timelyReportRepository.findByStartupIdAndDraftReportTrue(startup.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found for founder: " + founderUserId));

        return draft;
    }

    public List<TimelyReport> getReportsByFounder(String founderUserId) {
        return timelyReportRepository.findByFounderUserId(founderUserId);
    }
}
