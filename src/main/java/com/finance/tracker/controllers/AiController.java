package com.finance.tracker.controllers;

import com.finance.tracker.models.Category;
import com.finance.tracker.models.Transaction;
import com.finance.tracker.payload.response.MessageResponse;
import com.finance.tracker.repositories.CategoryRepository;
import com.finance.tracker.repositories.TransactionRepository;
import com.finance.tracker.security.services.UserDetailsImpl;
import com.finance.tracker.services.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping("/category-prediction")
    public ResponseEntity<?> predictCategory(@RequestParam("description") String description, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Category> userCategories = categoryRepository.findByUserId(userDetails.getId());
        List<String> categoryNames = userCategories.stream().map(Category::getName).collect(Collectors.toList());

        String predictedCategory = aiService.predictCategory(description, categoryNames);

        Map<String, Object> response = new HashMap<>();
        response.put("category", predictedCategory);
        
        // Find if we have a match
        Long categoryId = null;
        for (Category cat : userCategories) {
            if (cat.getName().equalsIgnoreCase(predictedCategory.trim())) {
                categoryId = cat.getId();
                break;
            }
        }
        response.put("categoryId", categoryId);
        response.put("confidence", 0.95); // placeholder

        return ResponseEntity.ok(response);
    }

    @GetMapping("/insights")
    public ResponseEntity<?> getInsights(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Transaction> transactions = transactionRepository.findByUserId(userDetails.getId());
        
        double totalIncome = 0;
        double totalExpense = 0;
        Map<String, Double> categorySpending = new HashMap<>();

        for (Transaction t : transactions) {
            if (t.getCategory() != null) {
                if (t.getCategory().getType() == Category.CategoryType.INCOME) {
                    totalIncome += t.getAmount();
                } else {
                    totalExpense += t.getAmount();
                    categorySpending.put(t.getCategory().getName(), 
                        categorySpending.getOrDefault(t.getCategory().getName(), 0.0) + t.getAmount());
                }
            }
        }

        String insights = aiService.generateSpendingInsights(totalIncome, totalExpense, categorySpending);
        return ResponseEntity.ok(new MessageResponse(insights));
    }

    @GetMapping("/predictions")
    public ResponseEntity<?> getPredictions(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Transaction> transactions = transactionRepository.findByUserId(userDetails.getId());

        LocalDate today = LocalDate.now();
        LocalDate last30Days = today.minusDays(30);
        LocalDate last60Days = today.minusDays(60);

        double past30DaysSpent = 0;
        double past60DaysSpent = 0;

        for (Transaction t : transactions) {
            if (t.getCategory() != null && t.getCategory().getType() == Category.CategoryType.EXPENSE) {
                if (t.getDate().isAfter(last30Days) || t.getDate().isEqual(last30Days)) {
                    past30DaysSpent += t.getAmount();
                } else if (t.getDate().isAfter(last60Days)) {
                    past60DaysSpent += t.getAmount();
                }
            }
        }

        String warning = aiService.predictBudget(past30DaysSpent, past60DaysSpent);
        
        Map<String, Object> response = new HashMap<>();
        response.put("predictedExpense", past30DaysSpent * 1.05); // naive 5% increase fallback prediction
        response.put("warning", warning);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Transaction> transactions = transactionRepository.findByUserId(userDetails.getId());
        
        double totalIncome = 0;
        double totalExpense = 0;
        for (Transaction t : transactions) {
             if (t.getCategory() != null) {
                if (t.getCategory().getType() == Category.CategoryType.INCOME) {
                    totalIncome += t.getAmount();
                } else {
                    totalExpense += t.getAmount();
                }
             }
        }

        String userContext = "User has $" + totalIncome + " income and $" + totalExpense + " expenses.";
        String question = request.getOrDefault("question", "Hello");

        String answer = aiService.chatWithAi(userContext, question);
        return ResponseEntity.ok(new MessageResponse(answer));
    }

    @PostMapping("/categorize")
    public ResponseEntity<?> categorize(@RequestBody Map<String, String> request, Authentication authentication) {
        String description = request.getOrDefault("description", "");
        String category = aiService.categorizeTransaction(description);
        Map<String, Object> response = new HashMap<>();
        response.put("category", category);
        return ResponseEntity.ok(response);
    }
}
