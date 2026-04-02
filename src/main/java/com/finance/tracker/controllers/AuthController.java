package com.finance.tracker.controllers;

import com.finance.tracker.models.User;
import com.finance.tracker.models.PasswordResetToken;
import com.finance.tracker.payload.request.LoginRequest;
import com.finance.tracker.payload.request.ForgotPasswordRequest;
import com.finance.tracker.payload.request.SignupRequest;
import com.finance.tracker.payload.request.ResetPasswordRequest;
import com.finance.tracker.payload.response.JwtResponse;
import com.finance.tracker.models.Category;
import com.finance.tracker.payload.response.MessageResponse;
import com.finance.tracker.repositories.CategoryRepository;
import com.finance.tracker.repositories.PasswordResetTokenRepository;
import com.finance.tracker.repositories.UserRepository;
import com.finance.tracker.security.jwt.JwtUtils;
import com.finance.tracker.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    PasswordResetTokenRepository passwordResetTokenRepository;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login requested for username={}", loginRequest.getUsername());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail()));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        logger.info("Signup requested for username={}", signUpRequest.getUsername());
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(encoder.encode(signUpRequest.getPassword()));

        userRepository.save(user);

        // Preload default categories
        String[] defaultExpenses = {"Food", "Rent", "Travel", "Utilities", "Entertainment"};
        for (String catName : defaultExpenses) {
            Category cat = new Category();
            cat.setName(catName);
            cat.setType(Category.CategoryType.EXPENSE);
            cat.setUser(user);
            categoryRepository.save(cat);
        }
        Category salaryCat = new Category();
        salaryCat.setName("Salary");
        salaryCat.setType(Category.CategoryType.INCOME);
        salaryCat.setUser(user);
        categoryRepository.save(salaryCat);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        logger.info("Password reset requested. identifier={}", request.getIdentifier());

        String identifier = request.getIdentifier();
        Optional<User> userOptional = userRepository.findByUsername(identifier);
        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByEmail(identifier);
        }

        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid credentials"));
        }

        User user = userOptional.get();

        // Demo mode: generate temp password + reset token, return both.
        String resetToken = UUID.randomUUID().toString();
        String tempPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        user.setPassword(encoder.encode(tempPassword));
        userRepository.save(user);

        PasswordResetToken tokenEntity = new PasswordResetToken();
        tokenEntity.setToken(resetToken);
        tokenEntity.setUser(user);
        tokenEntity.setExpiryDate(LocalDateTime.now().plusMinutes(15));
        tokenEntity.setUsed(false);
        passwordResetTokenRepository.save(tokenEntity);

        Map<String, Object> response = new HashMap<>();
        response.put("resetToken", resetToken);
        response.put("tempPassword", tempPassword);

        logger.info("Password reset token generated for userId={}", user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("Password reset confirm requested.");

        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByToken(request.getToken());
        if (tokenOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired token"));
        }

        PasswordResetToken token = tokenOptional.get();
        if (token.isUsed() || token.getExpiryDate() == null || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Invalid or expired token"));
        }

        User user = token.getUser();
        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        logger.info("Password reset successful for userId={}", user.getId());
        return ResponseEntity.ok(new MessageResponse("Password reset successfully!"));
    }
}
