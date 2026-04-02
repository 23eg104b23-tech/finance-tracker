package com.finance.tracker.services;

import com.finance.tracker.models.Category;
import com.finance.tracker.models.User;
import com.finance.tracker.repositories.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);

    @Autowired
    private CategoryRepository categoryRepository;
    
    public List<Category> fetchAllCategories(Long userId) {
        logger.info("Fetching all categories for user ID: {}", userId);
        return categoryRepository.findByUserId(userId);
    }
    
    public void initializeCategories(User user) {
        if (user == null) {
            logger.error("Cannot initialize categories: User is null");
            return;
        }
        
        logger.info("Initializing default categories for user ID: {}", user.getId());
        
        createCategoryIfNotExists(user, "Food", Category.CategoryType.EXPENSE);
        createCategoryIfNotExists(user, "Rent", Category.CategoryType.EXPENSE);
        createCategoryIfNotExists(user, "Travel", Category.CategoryType.EXPENSE);
        createCategoryIfNotExists(user, "Utilities", Category.CategoryType.EXPENSE);
        createCategoryIfNotExists(user, "Entertainment", Category.CategoryType.EXPENSE);
        // And an income one for good measure
        createCategoryIfNotExists(user, "Salary", Category.CategoryType.INCOME);
        
        logger.info("Category initialization completed for user ID: {}", user.getId());
    }
    
    private void createCategoryIfNotExists(User user, String name, Category.CategoryType type) {
        if (!categoryRepository.existsByNameAndUserId(name, user.getId())) {
            Category category = new Category();
            category.setName(name);
            category.setType(type);
            category.setUser(user);
            categoryRepository.save(category);
        }
    }
}
