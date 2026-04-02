package com.finance.tracker.controllers;

import com.finance.tracker.models.Category;
import com.finance.tracker.models.Transaction;
import com.finance.tracker.payload.response.ReportData;
import com.finance.tracker.repositories.TransactionRepository;
import com.finance.tracker.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    TransactionRepository transactionRepository;

    @GetMapping("/monthly")
    public ResponseEntity<List<ReportData>> getMonthlyReport(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Transaction> transactions = transactionRepository.findByUserId(userDetails.getId());

        Map<String, ReportData> monthlyData = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy"); // e.g., "Jan 2026"

        for (Transaction t : transactions) {
            if (t.getDate() == null) continue;
            
            String label = t.getDate().format(formatter);
            ReportData data = monthlyData.computeIfAbsent(label, k -> new ReportData(k, 0.0, 0.0));

            Category.CategoryType type = t.getType() != null ? t.getType() : (t.getCategory() != null ? t.getCategory().getType() : null);
            Double amount = t.getAmount() != null ? t.getAmount() : 0.0;

            if (type == Category.CategoryType.INCOME) {
                data.setIncome(data.getIncome() + amount);
            } else if (type == Category.CategoryType.EXPENSE) {
                data.setExpense(data.getExpense() + amount);
            }
        }

        // Sort by date logic (could be improved by sorting map keys as LocalDate)
        List<ReportData> result = new ArrayList<>(monthlyData.values());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/yearly")
    public ResponseEntity<List<ReportData>> getYearlyReport(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Transaction> transactions = transactionRepository.findByUserId(userDetails.getId());

        Map<String, ReportData> yearlyData = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy");

        for (Transaction t : transactions) {
            if (t.getDate() == null) continue;
            
            String label = t.getDate().format(formatter);
            ReportData data = yearlyData.computeIfAbsent(label, k -> new ReportData(k, 0.0, 0.0));

            Category.CategoryType type = t.getType() != null ? t.getType() : (t.getCategory() != null ? t.getCategory().getType() : null);
            Double amount = t.getAmount() != null ? t.getAmount() : 0.0;

            if (type == Category.CategoryType.INCOME) {
                data.setIncome(data.getIncome() + amount);
            } else if (type == Category.CategoryType.EXPENSE) {
                data.setExpense(data.getExpense() + amount);
            }
        }

        List<ReportData> result = new ArrayList<>(yearlyData.values());
        // Sort by year string naturally
        result.sort(Comparator.comparing(ReportData::getLabel));
        
        return ResponseEntity.ok(result);
    }
}
