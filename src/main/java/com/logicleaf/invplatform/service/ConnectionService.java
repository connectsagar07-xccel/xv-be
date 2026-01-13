package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.exception.BadRequestException;
import com.logicleaf.invplatform.exception.ResourceNotFoundException;
import com.logicleaf.invplatform.model.*;
import com.logicleaf.invplatform.repository.*;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConnectionService {

        private final StartupInvestorMappingRepository mappingRepository;
        private final UserRepository userRepository;
        private final StartupRepository startupRepository;
        private final InvestorRepository investorRepository;
        private final MailService mailService;

        // Founder invites an investor
        public StartupInvestorMapping inviteInvestor(String founderEmail, String investorEmail,
                        InvestorRole investorRole) {
                User founderUser = userRepository.findByEmail(founderEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("Founder not found."));
                Startup startup = startupRepository.findByFounderUserId(founderUser.getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Startup not found."));

                Optional<StartupInvestorMapping> existingMappingOpt = mappingRepository
                                .findByStartupIdAndInvestorEmail(startup.getId(), investorEmail);

                if (existingMappingOpt.isPresent()) {
                        StartupInvestorMapping existingMapping = existingMappingOpt.get();

                        switch (existingMapping.getStatus()) {
                                case INVITED -> throw new BadRequestException(
                                                "An invitation has already been sent to this investor.");
                                case PENDING ->
                                        throw new BadRequestException(
                                                        "This investor has already requested a connection.");
                                case ACTIVE -> throw new BadRequestException(
                                                "This investor is already connected to your startup.");
                                default -> throw new BadRequestException(
                                                "A connection already exists between this startup and investor.");
                        }
                }

                StartupInvestorMapping mapping = StartupInvestorMapping.builder()
                                .startupId(startup.getId())
                                .investorRole(investorRole)
                                .investorEmail(investorEmail)
                                .status(MappingStatus.INVITED)
                                .build();

                mapping = mappingRepository.save(mapping);

                try {
                        mailService.sendConnectionEmail(investorEmail, startup.getStartupName(),
                                        mapping.getId(), true);
                } catch (Exception e) {
                        System.err.println("Failed to send invite email: " + e.getMessage());
                }

                return mapping;
        }

        // Investor requests connection with a startup
        public StartupInvestorMapping requestConnection(String investorEmail, String startupId,
                        InvestorRole investorRole) {
                User investorUser = userRepository.findByEmail(investorEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("Investor not found."));
                Investor investor = investorRepository.findByUserId(investorUser.getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor profile not found."));
                Startup startup = startupRepository.findById(startupId)
                                .orElseThrow(() -> new ResourceNotFoundException("Startup not found."));

                Optional<StartupInvestorMapping> existingMappingOpt = mappingRepository
                                .findByStartupIdAndInvestorId(startup.getId(), investor.getId());

                if (existingMappingOpt.isPresent()) {
                        StartupInvestorMapping existingMapping = existingMappingOpt.get();

                        switch (existingMapping.getStatus()) {
                                case INVITED -> throw new BadRequestException(
                                                "An invitation has already been sent to this investor.");
                                case PENDING ->
                                        throw new BadRequestException(
                                                        "This investor has already requested a connection.");
                                case ACTIVE -> throw new BadRequestException(
                                                "This investor is already connected to your startup.");
                                default -> throw new BadRequestException(
                                                "A connection already exists between this startup and investor.");
                        }
                }

                StartupInvestorMapping mapping = StartupInvestorMapping.builder()
                                .startupId(startup.getId())
                                .investorId(investor.getId())
                                .investorRole(investorRole) // ✅ added
                                .status(MappingStatus.PENDING)
                                .build();

                mapping = mappingRepository.save(mapping);

                User founderUser = userRepository.findById(startup.getFounderUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Founder not found."));

                try {
                        mailService.sendConnectionEmail(founderUser.getEmail(), investor.getFirmName(), mapping.getId(),
                                        false);
                } catch (Exception e) {
                        System.err.println("Failed to send request email: " + e.getMessage());
                }

                return mapping;
        }

        // Founder approves a PENDING request
        public StartupInvestorMapping approveConnection(String mappingId) {
                StartupInvestorMapping m = mappingRepository.findById(mappingId)
                                .orElseThrow(() -> new ResourceNotFoundException("Connection not found."));

                if (m.getStatus() != MappingStatus.PENDING) {
                        throw new ResourceNotFoundException("Only pending requests can be approved by founder.");
                }

                // load both sides for notifications
                Startup startup = startupRepository.findById(m.getStartupId())
                                .orElseThrow(() -> new ResourceNotFoundException("Startup not found."));
                Investor investor = investorRepository.findById(m.getInvestorId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor not found."));
                User founderUser = userRepository.findById(startup.getFounderUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Founder user not found."));
                User investorUser = userRepository.findById(investor.getUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor user not found."));

                m.setStatus(MappingStatus.ACTIVE);
                StartupInvestorMapping saved = mappingRepository.save(m);

                // notify investor that founder approved
                try {
                        mailService.sendConnectionStatusEmail(
                                        founderUser.getEmail(),
                                        founderUser.getName(),
                                        investorUser.getEmail(),
                                        startup.getStartupName(),
                                        "approved",
                                        "founder");
                } catch (Exception ignored) {
                }

                return saved;
        }

        // Investor accepts an INVITED invite
        public StartupInvestorMapping acceptInvitation(String mappingId, String investorEmail) {

                // 1. Find mapping
                StartupInvestorMapping m = mappingRepository.findByIdAndInvestorEmail(mappingId, investorEmail)
                                .orElseThrow(() -> new BadRequestException("invitation is invalid"));

                if (m.getStatus() != MappingStatus.INVITED) {
                        throw new BadRequestException("This invitation is invalid or has expired.");
                }

                // 2. Find investor by email
                User investorUser = userRepository.findByEmail(investorEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("Investor user not found."));

                Investor investor = investorRepository.findByUserId(investorUser.getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor record not found."));

                // 3. Find startup
                Startup startup = startupRepository.findById(m.getStartupId())
                                .orElseThrow(() -> new ResourceNotFoundException("Startup not found."));

                User founderUser = userRepository.findById(startup.getFounderUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Founder user not found."));

                // 4. Attach investorId NOW (not during invitation)
                m.setInvestorId(investor.getId());

                // 5. Set status to active
                m.setStatus(MappingStatus.ACTIVE);

                StartupInvestorMapping saved = mappingRepository.save(m);

                // 6. Notify founder
                try {
                        mailService.sendConnectionStatusEmail(
                                        investorUser.getEmail(),
                                        investorUser.getName(),
                                        founderUser.getEmail(),
                                        startup.getStartupName(),
                                        "accepted",
                                        "investor");
                } catch (Exception ignored) {
                }

                return saved;
        }

        // Founder rejects a PENDING request (delete mapping)
        public void rejectByFounder(String mappingId) {
                StartupInvestorMapping m = mappingRepository.findById(mappingId)
                                .orElseThrow(() -> new ResourceNotFoundException("Connection not found."));

                Startup startup = startupRepository.findById(m.getStartupId())
                                .orElseThrow(() -> new ResourceNotFoundException("Startup not found."));
                Investor investor = investorRepository.findById(m.getInvestorId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor not found."));
                User founderUser = userRepository.findById(startup.getFounderUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Founder user not found."));
                User investorUser = userRepository.findById(investor.getUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor user not found."));

                // optional status gate:
                if (m.getStatus() != MappingStatus.PENDING) {
                        throw new BadRequestException("Only pending requests can be rejected by founder.");
                }

                // notify investor, then delete
                try {
                        mailService.sendRejectionEmail(
                                        investorUser.getEmail(), founderUser.getName(), startup.getStartupName(), true);
                } catch (Exception ignored) {
                }

                mappingRepository.deleteById(mappingId);
        }

        // Investor rejects an INVITED invite (delete mapping)
        public void rejectByInvestor(String mappingId) {
                StartupInvestorMapping m = mappingRepository.findById(mappingId)
                                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found."));

                Startup startup = startupRepository.findById(m.getStartupId())
                                .orElseThrow(() -> new ResourceNotFoundException("Startup not found."));
                Investor investor = investorRepository.findById(m.getInvestorId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor not found."));
                User founderUser = userRepository.findById(startup.getFounderUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Founder user not found."));
                User investorUser = userRepository.findById(investor.getUserId())
                                .orElseThrow(() -> new ResourceNotFoundException("Investor user not found."));

                if (m.getStatus() != MappingStatus.INVITED) {
                        throw new BadRequestException("Only invited connections can be rejected by investor.");
                }

                try {
                        mailService.sendRejectionEmail(
                                        founderUser.getEmail(), investorUser.getName(), startup.getStartupName(),
                                        false);
                } catch (Exception ignored) {
                }

                mappingRepository.deleteById(mappingId);
        }

}
