package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.dto.DealPipelineDTO;
import com.logicleaf.invplatform.dto.DealPipelineDashboardDTO;
import com.logicleaf.invplatform.dto.DealPipelineRequestDTO;
import com.logicleaf.invplatform.model.DealPipeline;
import com.logicleaf.invplatform.model.DealStatus;
import com.logicleaf.invplatform.model.Startup;
import com.logicleaf.invplatform.model.TimelyReport;
import com.logicleaf.invplatform.repository.DealPipelineRepository;
import com.logicleaf.invplatform.repository.StartupRepository;
import com.logicleaf.invplatform.repository.StartupInvestorMappingRepository;
import com.logicleaf.invplatform.repository.TimelyReportRepository;
import com.logicleaf.invplatform.exception.BadRequestException;
import com.logicleaf.invplatform.exception.ResourceNotFoundException;
import com.logicleaf.invplatform.model.MappingStatus;
import com.logicleaf.invplatform.model.StartupInvestorMapping;
import java.time.Instant;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DealPipelineService {

    private final DealPipelineRepository dealPipelineRepository;
    private final StartupRepository startupRepository;
    private final TimelyReportRepository timelyReportRepository;
    private final StartupInvestorMappingRepository startupInvestorMappingRepository;

    public List<DealPipelineDTO> getPipelineForInvestor(String investorId, DealStatus filter) {
        List<DealPipeline> pipelines = dealPipelineRepository.findByInvestorId(investorId);

        if (filter != null) {
            pipelines = pipelines.stream().filter(p -> p.getStatus() == filter)
                    .collect(Collectors.toList());
        }

        List<String> startupIds = pipelines.stream()
                .map(DealPipeline::getStartupId)
                .collect(Collectors.toList());

        Map<String, Startup> startupMap = startupRepository.findAllById(startupIds).stream()
                .collect(Collectors.toMap(Startup::getId, Function.identity()));

        return pipelines.stream().map(pipeline -> {
            Startup startup = startupMap.get(pipeline.getStartupId());
            return mapToDTO(pipeline, startup);
        }).collect(Collectors.toList());
    }

    public DealPipelineDTO addToPipeline(DealPipelineRequestDTO request) {
        // Validate startup exists
        if (!startupRepository.existsById(request.getStartupId())) {
            throw new ResourceNotFoundException("Startup not found");
        }

        // Check for duplicate
        if (dealPipelineRepository.findByInvestorIdAndStartupId(request.getInvestorId(), request.getStartupId())
                .isPresent()) {
            throw new BadRequestException("Startup already in deal pipeline");
        }

        // Validate connection
        StartupInvestorMapping mapping = startupInvestorMappingRepository
                .findByStartupIdAndInvestorId(request.getStartupId(), request.getInvestorId())
                .orElseThrow(() -> new BadRequestException("No connection found between investor and startup"));

        if (mapping.getStatus() != MappingStatus.ACTIVE) {
            throw new BadRequestException("Connection is not active");
        }

        DealPipeline pipeline = DealPipeline.builder()
                .startupId(request.getStartupId())
                .investorId(request.getInvestorId())
                .status(request.getStatus())
                .build();

        pipeline = dealPipelineRepository.save(pipeline);
        Startup startup = startupRepository.findById(pipeline.getStartupId()).orElse(null);
        return mapToDTO(pipeline, startup);
    }

    public DealPipelineDTO updatePipeline(String id, DealPipelineRequestDTO request) {
        DealPipeline pipeline = dealPipelineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal Pipeline not found"));

        // Validate connection still exists/active (optional but good practice based on
        // requirements)
        StartupInvestorMapping mapping = startupInvestorMappingRepository
                .findByStartupIdAndInvestorId(pipeline.getStartupId(), pipeline.getInvestorId())
                .orElseThrow(() -> new BadRequestException("No connection found between investor and startup"));

        if (mapping.getStatus() != MappingStatus.ACTIVE) {
            throw new BadRequestException("Connection is not active");
        }

        pipeline.setStatus(request.getStatus());

        pipeline = dealPipelineRepository.save(pipeline);
        Startup startup = startupRepository.findById(pipeline.getStartupId()).orElse(null);
        return mapToDTO(pipeline, startup);
    }

    public void removeFromPipeline(String id) {
        dealPipelineRepository.deleteById(id);
    }

    public DealPipelineDashboardDTO getDashboardMetrics(String investorId) {
        List<DealPipeline> pipelines = dealPipelineRepository.findByInvestorId(investorId);

        long totalPipeline = pipelines.size();
        long starredDeals = pipelines.stream().filter(p -> p.getStatus() == DealStatus.STARRED_DEAL).count();
        long hotDeals = pipelines.stream().filter(p -> p.getStatus() == DealStatus.HOT_DEAL).count();

        List<String> startupIds = pipelines.stream()
                .map(DealPipeline::getStartupId)
                .collect(Collectors.toList());

        double totalFunding = startupRepository.findAllById(startupIds).stream()
                .mapToDouble(s -> s.getFundingRaised() != null ? s.getFundingRaised() : 0.0)
                .sum();

        return DealPipelineDashboardDTO.builder()
                .totalPipeline(totalPipeline)
                .starredDeals(starredDeals)
                .hotDeals(hotDeals)
                .totalValue("₹" + totalFunding)
                .build();
    }

    private DealPipelineDTO mapToDTO(DealPipeline pipeline, Startup startup) {
        String lastActivity = "N/A";
        String valuation = "N/A";

        if (startup != null) {
            // Get valuation
            if (startup.getValuation() != null) {
                valuation = "₹" + startup.getValuation();
            }

            // Get last activity
            Long lastActivityTime = null;
            TimelyReport lastReport = timelyReportRepository.findFirstByStartupIdOrderByCreatedAtDesc(startup.getId())
                    .orElse(null);

            if (lastReport != null) {
                lastActivityTime = lastReport.getCreatedAt();
            } else {
                lastActivityTime = startup.getCreatedAt() != null ? startup.getCreatedAt().toEpochMilli() : null;
            }

            if (lastActivityTime != null) {
                lastActivity = getRelativeTime(lastActivityTime);
            }
        }

        DealPipelineDTO.DealPipelineDTOBuilder builder = DealPipelineDTO.builder()
                .id(pipeline.getId())
                .startupId(pipeline.getStartupId())
                .investorId(pipeline.getInvestorId())
                .status("Hot")
                .dealStatus(pipeline.getStatus())
                .valuation(valuation)
                .lastActivity(lastActivity);

        if (startup != null) {
            builder.startupName(startup.getStartupName())
                    .industry(startup.getSector() != null ? startup.getSector().name() : "Unknown")
                    .stage(startup.getStage())
                    .funding(startup.getFundingRaised() != null ? "₹" + startup.getFundingRaised() : "₹0");
        } else {
            builder.startupName("Unknown Startup")
                    .industry("Unknown")
                    .stage("Seed")
                    .funding("₹0");
        }

        return builder.build();
    }

    private String getRelativeTime(Long timeInMillis) {
        if (timeInMillis == null)
            return "N/A";

        Instant now = Instant.now();
        Instant past = Instant.ofEpochMilli(timeInMillis);
        Duration duration = Duration.between(past, now);

        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return "Just now";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (seconds < 604800) { // 7 days
            long days = seconds / 86400;
            return days + (days == 1 ? " day ago" : " days ago");
        } else if (seconds < 2592000) { // 30 days
            long weeks = seconds / 604800;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        } else if (seconds < 31536000) { // 365 days
            long months = seconds / 2592000;
            return months + (months == 1 ? " month ago" : " months ago");
        } else {
            long years = seconds / 31536000;
            return years + (years == 1 ? " year ago" : " years ago");
        }
    }
}
