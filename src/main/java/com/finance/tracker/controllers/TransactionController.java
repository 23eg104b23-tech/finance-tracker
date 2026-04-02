package com.finance.tracker.controllers;

import com.finance.tracker.models.Category;
import com.finance.tracker.models.Transaction;
import com.finance.tracker.models.User;
import com.finance.tracker.payload.response.MessageResponse;
import com.finance.tracker.repositories.CategoryRepository;
import com.finance.tracker.repositories.TransactionRepository;
import com.finance.tracker.repositories.UserRepository;
import com.finance.tracker.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    CategoryRepository categoryRepository;
    
    @Autowired
    UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<Transaction> transactions = transactionRepository.findByUserId(userDetails.getId());
        return ResponseEntity.ok(transactions);
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody Transaction transactionRequest, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Optional<User> userOptional = userRepository.findById(userDetails.getId());
        
        if (!userOptional.isPresent()) return ResponseEntity.badRequest().body(new MessageResponse("Error: User not found."));
        
        Optional<Category> categoryOptional = categoryRepository.findById(transactionRequest.getCategory().getId());
        if (!categoryOptional.isPresent()) return ResponseEntity.badRequest().body(new MessageResponse("Error: Category not found."));
        
        Category category = categoryOptional.get();
        // Allow both user-owned categories and global/system categories (user == null).
        // If category is user-owned, enforce ownership.
        if (category.getUser() != null && !category.getUser().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized category access."));
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(transactionRequest.getAmount());
        transaction.setType(transactionRequest.getType() != null ? transactionRequest.getType() : category.getType());
        transaction.setDate(transactionRequest.getDate() != null ? transactionRequest.getDate() : LocalDate.now());
        transaction.setDescription(transactionRequest.getDescription());
        transaction.setCategory(category);
        transaction.setUser(userOptional.get());
        transaction.setAiCategory(transactionRequest.getAiCategory());
        transaction.setPredictionConfidence(transactionRequest.getPredictionConfidence());
        
        transactionRepository.save(transaction);
        return ResponseEntity.ok(transaction);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable("id") Long id, @RequestBody Transaction transactionRequest, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Optional<Transaction> transactionData = transactionRepository.findById(id);

        if (transactionData.isPresent()) {
            Transaction transaction = transactionData.get();
            if (!transaction.getUser().getId().equals(userDetails.getId())) {
                 return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized to update."));
            }

            if (transactionRequest.getCategory() != null && transactionRequest.getCategory().getId() != null) {
                Optional<Category> categoryOptional = categoryRepository.findById(transactionRequest.getCategory().getId());
                if (categoryOptional.isPresent()) {
                    Category selected = categoryOptional.get();
                    // Allow both user-owned categories and system/global categories (user == null).
                    if (selected.getUser() == null || selected.getUser().getId().equals(userDetails.getId())) {
                        transaction.setCategory(selected);
                    } else {
                        return ResponseEntity.status(403).body(new MessageResponse("Error: Invalid category."));
                    }
                } else {
                    return ResponseEntity.status(403).body(new MessageResponse("Error: Invalid category."));
                }
            }

            if (transactionRequest.getAmount() != null) transaction.setAmount(transactionRequest.getAmount());
            if (transactionRequest.getType() != null) transaction.setType(transactionRequest.getType());
            if (transactionRequest.getDate() != null) transaction.setDate(transactionRequest.getDate());
            if (transactionRequest.getDescription() != null) transaction.setDescription(transactionRequest.getDescription());
            if (transactionRequest.getAiCategory() != null) transaction.setAiCategory(transactionRequest.getAiCategory());
            if (transactionRequest.getPredictionConfidence() != null) transaction.setPredictionConfidence(transactionRequest.getPredictionConfidence());

            return ResponseEntity.ok(transactionRepository.save(transaction));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable("id") Long id, Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Optional<Transaction> transactionData = transactionRepository.findById(id);

        if (transactionData.isPresent()) {
            Transaction transaction = transactionData.get();
            if (transaction.getUser().getId().equals(userDetails.getId())) {
                transactionRepository.deleteById(id);
                return ResponseEntity.ok(new MessageResponse("Transaction deleted successfully!"));
            } else {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized to delete."));
            }
        }
        return ResponseEntity.notFound().build();
    }
}
