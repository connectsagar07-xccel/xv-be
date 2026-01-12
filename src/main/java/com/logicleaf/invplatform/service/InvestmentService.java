package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.dto.InvestmentRequestDTO;
import com.logicleaf.invplatform.dto.PortfolioCompanyDTO;
import com.logicleaf.invplatform.dto.PortfolioDashboardDTO;
import com.logicleaf.invplatform.exception.BadRequestException;
import com.logicleaf.invplatform.exception.ResourceNotFoundException;
import com.logicleaf.invplatform.model.Investment;
import com.logicleaf.invplatform.model.MappingStatus;
import com.logicleaf.invplatform.model.Startup;
import com.logicleaf.invplatform.model.StartupInvestorMapping;
import com.logicleaf.invplatform.model.TimelyReport;
import com.logicleaf.invplatform.repository.InvestmentRepository;
import com.logicleaf.invplatform.repository.StartupInvestorMappingRepository;
import com.logicleaf.invplatform.repository.StartupRepository;
import com.logicleaf.invplatform.repository.TimelyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final StartupRepository startupRepository;
    private final StartupInvestorMappingRepository startupInvestorMappingRepository;
    private final TimelyReportRepository timelyReportRepository;

    public void addInvestment(String investorId, InvestmentRequestDTO request) {
        // Validate startup exists
        if (!startupRepository.existsById(request.getStartupId())) {
            throw new ResourceNotFoundException("Startup not found");
        }

        // Validate connection
        StartupInvestorMapping mapping = startupInvestorMappingRepository
                .findByStartupIdAndInvestorId(request.getStartupId(), investorId)
                .orElseThrow(() -> new BadRequestException("No connection found between investor and startup"));

        if (mapping.getStatus() != MappingStatus.ACTIVE) {
            throw new BadRequestException("Connection is not active");
        }

        Optional<Investment> existingInvestment = investmentRepository.findByInvestorIdAndStartupId(investorId,
                request.getStartupId());

        Investment investment;
        if (existingInvestment.isPresent()) {
            investment = existingInvestment.get();
            investment.setTotalInvestedAmount(investment.getTotalInvestedAmount() + request.getAmount());
            investment.setOwnershipPercentage(request.getOwnershipPercentage()); // Overwrite ownership
        } else {
            investment = Investment.builder()
                    .investorId(investorId)
                    .startupId(request.getStartupId())
                    .totalInvestedAmount(request.getAmount())
                    .ownershipPercentage(request.getOwnershipPercentage())
                    .build();
        }

        investmentRepository.save(investment);
    }

    public List<PortfolioCompanyDTO> getPortfolio(String investorId) {
        List<Investment> investments = investmentRepository.findByInvestorId(investorId);

        return investments.stream().map(investment -> {
            Startup startup = startupRepository.findById(investment.getStartupId()).orElse(null);
            if (startup == null)
                return null;

            // Get MRR and Growth
            List<TimelyReport> reports = timelyReportRepository.findByStartupIdOrderByCreatedAtDesc(startup.getId());
            Double currentMrr = 0.0;
            Double growth = 0.0;

            if (!reports.isEmpty()) {
                TimelyReport latestReport = reports.get(0);
                currentMrr = latestReport.getMonthlyRevenue() != null ? latestReport.getMonthlyRevenue() : 0.0;

                if (reports.size() >= 2) {
                    TimelyReport previousReport = reports.get(1);
                    Double previousMrr = previousReport.getMonthlyRevenue() != null ? previousReport.getMonthlyRevenue()
                            : 0.0;
                    if (previousMrr > 0) {
                        growth = ((currentMrr - previousMrr) / previousMrr) * 100;
                    }
                }
            }

            String foundedYear = startup.getCreatedAt() != null
                    ? String.valueOf(startup.getCreatedAt().atZone(ZoneId.systemDefault()).getYear())
                    : "N/A";

            String lastUpdate = "N/A";
            if (investment.getUpdatedAt() != null) {
                lastUpdate = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
                        .withZone(ZoneId.systemDefault())
                        .format(investment.getUpdatedAt());
            } else if (investment.getCreatedAt() != null) {
                lastUpdate = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
                        .withZone(ZoneId.systemDefault())
                        .format(investment.getCreatedAt());
            }

            return PortfolioCompanyDTO.builder()
                    .startupId(startup.getId())
                    .startupName(startup.getStartupName())
                    .industry(startup.getSector() != null ? startup.getSector().name() : "Unknown")
                    .teamSize(startup.getTeamSize())
                    .foundedYear(foundedYear)
                    .stage(startup.getStage())
                    .status("Active") // Hardcoded for now
                    .investmentAmount(round(investment.getTotalInvestedAmount()))
                    .ownershipPercentage(round(investment.getOwnershipPercentage()))
                    .valuation(round(startup.getValuation()))
                    .mrr(round(currentMrr))
                    .growthPercentage(round(growth))
                    .lastUpdate(lastUpdate)
                    .build();
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    private Double round(Double value) {
        if (value == null)
            return null;
        return Math.round(value * 100.0) / 100.0;
    }

    public PortfolioDashboardDTO getDashboardMetrics(String investorId) {
        List<PortfolioCompanyDTO> portfolio = getPortfolio(investorId);

        int totalCompanies = portfolio.size();
        double totalInvested = portfolio.stream().mapToDouble(PortfolioCompanyDTO::getInvestmentAmount).sum();

        // Portfolio Value = Sum of (Valuation * (Ownership / 100))
        double portfolioValue = portfolio.stream()
                .mapToDouble(
                        p -> (p.getValuation() != null ? p.getValuation() : 0.0) * (p.getOwnershipPercentage() / 100.0))
                .sum();

        double avgGrowth = portfolio.stream().mapToDouble(PortfolioCompanyDTO::getGrowthPercentage).average()
                .orElse(0.0);

        return PortfolioDashboardDTO.builder()
                .totalCompanies(totalCompanies)
                .totalInvested(totalInvested)
                .portfolioValue(portfolioValue)
                .avgGrowth(avgGrowth)
                .build();
    }
}
