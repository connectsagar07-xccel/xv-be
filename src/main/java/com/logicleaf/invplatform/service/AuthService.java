package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.dto.*;
import com.logicleaf.invplatform.exception.BadRequestException;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.repository.UserRepository;
import com.logicleaf.invplatform.security.CustomUserDetails;
import com.logicleaf.invplatform.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MailService mailService;

    public User registerUser(SignUpRequest signUpRequest) {
        Optional<User> existingUserOpt = userRepository.findByEmail(signUpRequest.getEmail());

        
        if (existingUserOpt.isPresent() && existingUserOpt.get().isVerified()) {
            throw new RuntimeException("Email address already in use.");
        }

        User newUser = User.builder()
                .name(signUpRequest.getName())
                .email(signUpRequest.getEmail())
                .phone(signUpRequest.getPhone())
                .passwordHash(passwordEncoder.encode(signUpRequest.getPassword()))
                .role(signUpRequest.getRole())
                .isVerified(false)
                .onboarded(false)
                .build();

        String otp = generateOtp();
        newUser.setOtp(otp);
        newUser.setOtpExpiryTime(LocalDateTime.now().plusMinutes(10));
        newUser = userRepository.save(newUser);
        notificationService.sendOtp(newUser.getEmail(), otp);
        return newUser;
    }

    public boolean verifyOtp(VerifyOtpRequest verifyOtpRequest) {
        User user = userRepository.findByEmail(verifyOtpRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.isVerified()) {
            return true; // Already verified
        }

        if (user.getOtp().equals(verifyOtpRequest.getOtp()) && user.getOtpExpiryTime().isAfter(LocalDateTime.now())) {
            user.setVerified(true);
            user.setOtp(null); // Clear OTP after successful verification
            user.setOtpExpiryTime(null);
            userRepository.save(user);
            return true;
        }

        return false;
    }

    public void resendOtp(ResendOtpRequest resendOtpRequest) {
        User user = userRepository.findByEmail(resendOtpRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.isVerified()) {
            throw new RuntimeException("User is already verified.");
        }

        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiryTime(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);
        notificationService.sendOtp(user.getEmail(), otp);
    }

    public LoginResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        TokenResponse tokenResponse = new TokenResponse(jwt);
        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.isVerified(),
                user.isOnboarded());

        return new LoginResponse("success", "Login successful.", tokenResponse, userResponse);
    }

    private String generateOtp() {
        // Generate a 6-digit OTP
        SecureRandom random = new SecureRandom();
        int num = random.nextInt(999999);
        return String.format("%06d", num);
    }

    public void forgotPassword(ForgotPasswordDto forgotPasswordDto) {
        User user = userRepository.findByEmail(forgotPasswordDto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiryTime(LocalDateTime.now().plusHours(1)); // Token valid for 1 hour
        userRepository.save(user);

        mailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    public void resetPassword(ResetPasswordDto resetPasswordDto) {
        User user = userRepository.findByPasswordResetToken(resetPasswordDto.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid password reset token."));

        if (user.getPasswordResetTokenExpiryTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Password reset token has expired.");
        }

        user.setPasswordHash(passwordEncoder.encode(resetPasswordDto.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiryTime(null);
        userRepository.save(user);
    }
}
