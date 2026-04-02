package com.finance.tracker.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Service
public class AiService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${google.ai.api.key:}")
    private String googleApiKey;
    
    // We will use gpt-4o-mini as requested
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";
    private static final List<String> GEMINI_ALLOWED_CATEGORIES = List.of(
            "Food",
            "Travel",
            "Rent",
            "Utilities",
            "Entertainment"
    );
    private static final String GEMINI_URL_BASE =
            "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent?key=";
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    
    private final RestTemplate restTemplate;
    
    public AiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds connect
        factory.setReadTimeout(10000);   // 10 seconds read
        this.restTemplate = new RestTemplate(factory);
    }
    
    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("OPENAI_API_KEY is missing! AI features will fallback to default responses.");
        } else {
            logger.info("AI Service Initialized successfully.");
        }

        if (googleApiKey == null || googleApiKey.trim().isEmpty()) {
            logger.warn("GOOGLE_AI_API_KEY is missing! Gemini categorization will fallback to default.");
        }
    }

    public String predictCategory(String description, List<String> existingCategories) {
        // Prefer Gemini categorization when available.
        String geminiCategory = categorizeTransaction(description);
        if (geminiCategory != null && !"Other".equalsIgnoreCase(geminiCategory.trim())) {
            // Ensure it's part of existing categories to keep old endpoint behavior stable.
            for (String existing : existingCategories) {
                if (existing.equalsIgnoreCase(geminiCategory.trim())) {
                    return existing;
                }
            }
        }

        String prompt = "You are a financial category predictor. " +
            "Based on the description: '" + description + "', predict the category. " +
            "If it matches nicely with any of these existing categories: " + existingCategories + ", return EXACTLY that category name. " +
            "Otherwise, return a very short, generic category name (e.g. Food, Travel, Utilities, Rent, Health, Entertainment, Shopping). " +
            "Return ONLY the category name and nothing else.";
            
        return callOpenAi(prompt, "system").orElse("Other");
    }

    /**
     * Gemini categorization for transaction descriptions.
     * If it fails (missing key, network error, parse error) returns "Other".
     */
    public String categorizeTransaction(String description) {
        if (googleApiKey == null || googleApiKey.trim().isEmpty()) {
            return "Other";
        }
        if (description == null || description.trim().isEmpty()) {
            return "Other";
        }

        String prompt = "Categorize this transaction: " + description.trim() +
                " into one word from [Food, Travel, Rent, Utilities, Entertainment]";

        try {
            String url = GEMINI_URL_BASE + googleApiKey.trim();

            // Gemini generateContent request format.
            Map<String, Object> body = new HashMap<>();
            Map<String, Object> parts = new HashMap<>();
            parts.put("text", prompt);

            List<Map<String, Object>> partList = List.of(parts);
            Map<String, Object> content = new HashMap<>();
            content.put("parts", partList);
            body.put("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return "Other";
            }

            String raw = extractGeminiText(response.getBody());
            if (raw == null) return "Other";

            String normalized = raw.trim();
            if (normalized.isEmpty()) return "Other";

            // If Gemini returns extra punctuation/words, keep only the first token letters.
            String candidate = normalized.split("\\s+")[0].replaceAll("[^a-zA-Z]", "");

            for (String allowed : GEMINI_ALLOWED_CATEGORIES) {
                if (allowed.equalsIgnoreCase(candidate)) {
                    return allowed;
                }
            }

            return "Other";
        } catch (Exception e) {
            logger.error("Gemini categorizeTransaction failed: {}", e.getMessage());
            return "Other";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiText(Map<String, Object> responseBody) {
        try {
            Object candidatesObj = responseBody.get("candidates");
            if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) return null;

            Object firstCandidate = candidates.get(0);
            if (!(firstCandidate instanceof Map<?, ?> candidateMap)) return null;

            Object contentObj = candidateMap.get("content");
            if (!(contentObj instanceof Map<?, ?> contentMap)) return null;

            Object partsObj = contentMap.get("parts");
            if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) return null;

            Object firstPart = parts.get(0);
            if (!(firstPart instanceof Map<?, ?> partMap)) return null;

            Object textObj = partMap.get("text");
            if (textObj instanceof String text) return text;
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    public String generateSpendingInsights(double totalIncome, double totalExpense, Map<String, Double> categorySpending) {
         String prompt = "You are a friendly personal finance assistant. " +
            "The user has an income of $" + totalIncome + " and expenses of $" + totalExpense + ". " +
            "Category breakdown: " + categorySpending.toString() + ". " +
            "Provide 2-3 short, actionable insights or warnings based on this data. Keep it practical and concise.";
            
         return callOpenAi(prompt, "system").orElse("You have spent $" + totalExpense + " recently. Try to keep an eye on your budget!");
    }

    public String predictBudget(double past30DaysTotal, double past60DaysTotal) {
        String prompt = "You are a budget predictor. " +
            "The user spent $" + past30DaysTotal + " in the last 30 days, and $" + past60DaysTotal + " in the 30 days before that. " +
            "Based on this trend, predict their budget needs for the next month, and if they are overspending, add a gentle warning. " +
            "Keep the response to 1-2 short sentences.";
            
        return callOpenAi(prompt, "system").orElse("Unable to predict future budget at this moment. Stay vigilant.");
    }

    public String chatWithAi(String userContext, String userQuestion) {
        String prompt = "You are a friendly, helpful personal finance assistant. " +
            "Use the following user data context to answer the user's question accurately. " +
            "Context: " + userContext + "\n\n" +
            "Question: " + userQuestion + "\n\n" +
            "Answer directly and concisely.";
            
        return callOpenAi(prompt, "system").orElse("AI service unavailable, try again later.");
    }

    private Optional<String> callOpenAi(String message, String role) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Optional.empty();
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            // Constructing request body using Maps
            Map<String, Object> body = new HashMap<>();
            body.put("model", MODEL);
            body.put("temperature", 0.7);
            
            Map<String, String> messageObj = new HashMap<>();
            messageObj.put("role", role);
            messageObj.put("content", message);
            
            body.put("messages", Collections.singletonList(messageObj));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(OPENAI_URL, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageResp = (Map<String, Object>) choices.get(0).get("message");
                    return Optional.ofNullable((String) messageResp.get("content"));
                }
            }
        } catch (Exception e) {
            logger.error("Error retrieving AI response: {}", e.getMessage());
            return Optional.empty();
        }
        return Optional.empty();
    }
}
