package com.finance.tracker.controllers;

import com.finance.tracker.models.Category;
import com.finance.tracker.models.User;
import com.finance.tracker.payload.response.MessageResponse;
import com.finance.tracker.repositories.CategoryRepository;
import com.finance.tracker.repositories.UserRepository;
import com.finance.tracker.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories(Authentication authentication) {
        logger.info("Category fetch requested. Authenticated={}", authentication != null && authentication.isAuthenticated());

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            // Return public/empty categories if not authenticated
            return ResponseEntity.ok(categoryRepository.findAll().stream().filter(c -> c.getUser() == null).toList());
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Category> categories = categoryRepository.findByUserId(userDetails.getId());
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody Category categoryRequest, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            // Allow unauthenticated creation (system categories)
            Category category = new Category();
            category.setName(categoryRequest.getName());
            category.setType(categoryRequest.getType());
            categoryRepository.save(category);
            return ResponseEntity.ok(category);
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Optional<User> userOptional = userRepository.findById(userDetails.getId());

        if (userOptional.isPresent()) {
            Category category = new Category();
            category.setName(categoryRequest.getName());
            category.setType(categoryRequest.getType());
            category.setUser(userOptional.get());
            categoryRepository.save(category);
            return ResponseEntity.ok(category);
        }
        return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
    }

    @PostMapping("/init")
    public ResponseEntity<?> initCategories(Authentication authentication) {
        logger.info("Category initialization requested.");

        User user = null;
        boolean hasUserAuth = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        if (hasUserAuth) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            user = userRepository.findById(userDetails.getId()).orElse(null);
        }

        // Required defaults (plus optional "Salary" for income transactions already supported by the UI/dashboard).
        String[] defaultCategories = {"Food", "Rent", "Travel", "Utilities", "Entertainment"};

        for (String name : defaultCategories) {
            if (user != null) {
                // Prevent duplicates per user
                if (!categoryRepository.existsByNameAndUserId(name, user.getId())) {
                    Category category = new Category();
                    category.setName(name);
                    category.setType(Category.CategoryType.EXPENSE);
                    category.setUser(user);
                    categoryRepository.save(category);
                }
            } else {
                // Create global/system category if not already present
                if (!categoryRepository.existsByNameAndUserIsNull(name)) {
                    Category category = new Category();
                    category.setName(name);
                    category.setType(Category.CategoryType.EXPENSE);
                    category.setUser(null);
                    categoryRepository.save(category);
                }
            }
        }

        // Keep existing income support without changing the requirement list.
        if (user != null) {
            if (!categoryRepository.existsByNameAndUserId("Salary", user.getId())) {
                Category salary = new Category();
                salary.setName("Salary");
                salary.setType(Category.CategoryType.INCOME);
                salary.setUser(user);
                categoryRepository.save(salary);
            }
        } else {
            if (!categoryRepository.existsByNameAndUserIsNull("Salary")) {
                Category salary = new Category();
                salary.setName("Salary");
                salary.setType(Category.CategoryType.INCOME);
                salary.setUser(null);
                categoryRepository.save(salary);
            }
        }

        return ResponseEntity.ok(new MessageResponse("Categories initialized!"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable("id") Long id, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(new MessageResponse("Error: Unauthorized to delete."));
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Optional<Category> categoryData = categoryRepository.findById(id);

        if (categoryData.isPresent()) {
            Category category = categoryData.get();
            if (category.getUser() != null && category.getUser().getId().equals(userDetails.getId())) {
                categoryRepository.deleteById(id);
                return ResponseEntity.ok(new MessageResponse("Category deleted successfully!"));
            } else {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized to delete."));
            }
        }
        return ResponseEntity.notFound().build();
    }
}
