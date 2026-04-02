package com.finance.tracker.controllers;

import com.finance.tracker.models.Category;
import com.finance.tracker.models.Transaction;
import com.finance.tracker.payload.response.DashboardSummaryResponse;
import com.finance.tracker.repositories.TransactionRepository;
import com.finance.tracker.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    TransactionRepository transactionRepository;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Transaction> transactions = transactionRepository.findByUserId(userDetails.getId());

        double totalIncome = 0.0;
        double totalExpense = 0.0;

        for (Transaction t : transactions) {
            // First check if transaction explicitly has a type
            Category.CategoryType type = t.getType();
            if (type == null && t.getCategory() != null) {
                // Fallback to category type if transaction type is somehow missing
                type = t.getCategory().getType();
            }
            
            if (type == Category.CategoryType.INCOME) {
                totalIncome += t.getAmount() != null ? t.getAmount() : 0.0;
            } else if (type == Category.CategoryType.EXPENSE) {
                totalExpense += t.getAmount() != null ? t.getAmount() : 0.0;
            }
        }

        double balance = totalIncome - totalExpense;

        return ResponseEntity.ok(new DashboardSummaryResponse(totalIncome, totalExpense, balance));
    }
}
