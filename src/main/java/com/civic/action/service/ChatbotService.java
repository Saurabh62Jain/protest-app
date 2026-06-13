package com.civic.action.service;

import com.civic.action.model.mongo.Issue;
import com.civic.action.model.postgres.Candidate;
import com.civic.action.model.postgres.GeoBoundary;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.mongo.IssueRepository;
import com.civic.action.repository.postgres.CandidateRepository;
import com.civic.action.repository.postgres.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    @Value("${app.hf.token}")
    private String hfToken;

    @Value("${app.hf.model-url}")
    private String hfModelUrl;

    @Value("${app.psychiatry.url}")
    private String psychiatryUrl;

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final SpatialResolutionService spatialResolutionService;
    private final IssueRepository issueRepository;
    private final IssueWorkflowService issueWorkflowService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory user session state mapping sessionId -> ChatSession
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    @Data
    public static class ChatSession {
        private String state = "IDLE"; // IDLE, AWAITING_CONFIRMATION
        private String title;
        private String description;
        private String category;
        private String location;
        private double latitude;
        private double longitude;
        private String urgency;
        private int frustrationLevel;
        private String politicianName;
    }

    @Data
    public static class ChatResponse {
        private String reply;
        private String state;
        private String intent;
        private String urgency;
        private Integer frustrationLevel;
        private List<DuplicateIssueDto> duplicateIssues = new ArrayList<>();
    }

    @Data
    public static class DuplicateIssueDto {
        private String readableIssueId;
        private String title;
        private String description;
        private double similarityScore;
    }

    @Data
    private static class LlmExtractionResult {
        private String intent;
        private String location;
        private String category;
        private String description;
        @JsonProperty("frustration_level")
        private int frustrationLevel;
        private String urgency;
    }

    private static final String SYSTEM_PROMPT = 
            "You are an AI Triage Orchestrator for a citizen issue-reporting and emergency routing platform. Your sole objective is to analyze the user's message and extract key data points into a strict JSON format.\n" +
            "\n" +
            "Do not include any conversational text, greetings, markdown formatting outside of the JSON block, or explanations. Output ONLY valid, parsable JSON.\n" +
            "\n" +
            "# EXTRACTION RULES\n" +
            "1. \"intent\": Must be exactly one of [\"raise_issue\", \"police\", \"hospital\", \"psychiatric_help\", \"ngo\"].\n" +
            "2. \"location\": Extract the specific address, neighborhood, landmark, or city. If none is mentioned, output null.\n" +
            "3. \"category\": Identify the core topic (e.g., \"Road Infrastructure\", \"Water Supply\", \"Corruption\", \"Sanitation\"). If none applies or it's an emergency, output null.\n" +
            "4. \"description\": A concise, 1-2 sentence objective summary of the actual problem.\n" +
            "5. \"frustration_level\": An integer from 0 to 10 evaluating the user's anger or distress (0 = perfectly calm, 5 = annoyed, 10 = furious or in severe distress).\n" +
            "6. \"urgency\": Must be exactly one of [\"Low\", \"Medium\", \"High\", \"Critical\"]. \n" +
            "   - Critical: Immediate threat to life, active violent crime, or severe medical emergency.\n" +
            "   - High: Major public disruption (e.g., burst water main flooding streets, complete area power outage).\n" +
            "   - Medium: Standard complaints requiring attention (e.g., potholes, uncollected trash, broken streetlights).\n" +
            "   - Low: General inquiries, suggestions, or minor inconveniences.\n" +
            "\n" +
            "# EXAMPLES\n" +
            "\n" +
            "User Input: \"I am so sick and tired of this! There is a massive pothole right in front of the bakery on MG Road and I just popped my tire. Fix it now before someone gets hurt!\"\n" +
            "Output:\n" +
            "{\n" +
            "  \"intent\": \"raise_issue\",\n" +
            "  \"location\": \"MG Road, near bakery\",\n" +
            "  \"category\": \"Road Infrastructure\",\n" +
            "  \"description\": \"Massive pothole causing vehicle damage and posing a safety risk.\",\n" +
            "  \"frustration_level\": 8,\n" +
            "  \"urgency\": \"Medium\"\n" +
            "}\n" +
            "\n" +
            "User Input: \"I feel completely hopeless and I can't stop crying. I don't know what to do anymore, everything is too much.\"\n" +
            "Output:\n" +
            "{\n" +
            "  \"intent\": \"psychiatric_help\",\n" +
            "  \"location\": null,\n" +
            "  \"category\": null,\n" +
            "  \"description\": \"User is expressing severe emotional distress, hopelessness, and crying.\",\n" +
            "  \"frustration_level\": 9,\n" +
            "  \"urgency\": \"High\"\n" +
            "}\n" +
            "\n" +
            "User Input: \"Someone is breaking into the house next door at 42 Elm Street, please send someone immediately they have a weapon!\"\n" +
            "Output:\n" +
            "{\n" +
            "  \"intent\": \"police\",\n" +
            "  \"location\": \"42 Elm Street\",\n" +
            "  \"category\": \"Active Crime\",\n" +
            "  \"description\": \"Active break-in reported by a neighbor; perpetrator is armed.\",\n" +
            "  \"frustration_level\": 10,\n" +
            "  \"urgency\": \"Critical\"\n" +
            "}";

    public ChatResponse processChatMessage(String sessionId, String message, double latitude, double longitude, String userMobile) {
        ChatSession session = sessions.computeIfAbsent(sessionId, k -> new ChatSession());
        ChatResponse chatResponse = new ChatResponse();

        // Retrieve user
        User user = userRepository.findByMobileNumber(userMobile)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        // 1. Handle AWAITING_CONFIRMATION flow directly
        if ("AWAITING_CONFIRMATION".equals(session.getState())) {
            String lowerMessage = message.trim().toLowerCase();
            if (lowerMessage.matches("^(yes|yep|yeah|ok|sure|confirm|y|submit|go|go ahead|approve).*$")) {
                // Citizen approved: create the issue
                try {
                    // Update user's Voter ID check if not linked
                    if (user.getVoterId() == null || user.getVoterId().isBlank()) {
                        chatResponse.setReply("⚠️ A verified Voter ID is required to raise an issue. Please link your Voter ID in your settings or sidebar link before raising this issue.");
                        session.setState("IDLE");
                        chatResponse.setState("IDLE");
                        return chatResponse;
                    }

                    Issue issue = issueWorkflowService.createIssue(
                            user.getId(),
                            session.getTitle(),
                            session.getDescription(),
                            Collections.singletonList("https://images.unsplash.com/photo-1618477388954-7852f32655ec?q=80&w=600"), // Default UI image
                            session.getLatitude(),
                            session.getLongitude()
                    );

                    chatResponse.setReply("Ticket Number: `" + issue.getReadableIssueId() + "`. Your issue has been sent for approval under **" + session.getPoliticianName() + "**.");
                } catch (Exception e) {
                    log.error("Failed to raise issue from chatbot", e);
                    chatResponse.setReply("❌ There was an error creating your issue in the database: " + e.getMessage());
                } finally {
                    sessions.remove(sessionId); // clear session
                }
                chatResponse.setState("IDLE");
                return chatResponse;
            } else if (lowerMessage.matches("^(no|nop|nope|cancel|reject|n|don't).*$")) {
                chatResponse.setReply("❌ Issue submission cancelled. Let me know if you want to report something else or talk about another topic!");
                sessions.remove(sessionId); // clear session
                chatResponse.setState("IDLE");
                return chatResponse;
            } else {
                chatResponse.setReply("I didn't quite catch that. Would you like me to submit this issue to " + session.getPoliticianName() + "? Please reply with **Yes** or **No**.");
                chatResponse.setState("AWAITING_CONFIRMATION");
                return chatResponse;
            }
        }

        // 2. Call Hugging Face API to orchestrate & extract entities
        LlmExtractionResult extract;
        try {
            extract = callLlmOrchestrator(message);
            if (extract == null || extract.getIntent() == null) {
                throw new IllegalStateException("LLM response or intent is null");
            }
        } catch (Exception e) {
            log.error("LLM Extraction failed, falling back to basic parsing", e);
            extract = getFallbackExtraction(message);
        }

        chatResponse.setIntent(extract.getIntent());
        chatResponse.setUrgency(extract.getUrgency());
        chatResponse.setFrustrationLevel(extract.getFrustrationLevel());

        // 3. Routing logic based on Intent
        switch (extract.getIntent()) {
            case "police":
                chatResponse.setReply("🚨 **Emergency Police Dispatch Routing**\n\n" +
                        "It looks like you're reporting a police emergency or security threat at **" + (extract.getLocation() != null ? extract.getLocation() : "your location") + "**.\n\n" +
                        "📞 Please **dial 100** immediately to connect to emergency services. Your safety is paramount. Do not rely solely on this forum for active security issues.");
                chatResponse.setState("IDLE");
                break;

            case "hospital":
                chatResponse.setReply("🚑 **Medical Emergency Routing**\n\n" +
                        "This looks like an active medical emergency or hospital dispatch request.\n\n" +
                        "📞 Please **dial 102 (Ambulance)** or go to your nearest medical clinic immediately. We have notified local medical response guidelines. Please stay safe.");
                chatResponse.setState("IDLE");
                break;

            case "ngo":
                chatResponse.setReply("🏢 **NGO Support & Private Help Directory**\n\n" +
                        "For general community support or private help, we recommend contacting local organizations:\n" +
                        "- **Protest Support Foundation**: support@protestapp.org | 1800-222-HELP\n" +
                        "- **Community Action Group**: contact@communityaction.in\n\n" +
                        "They offer support for non-governmental complaints and welfare programs.");
                chatResponse.setState("IDLE");
                break;

            case "psychiatric_help":
                // Route to local Psychiatry Chatbot microservice
                try {
                    String psychReply = callPsychiatryBot(message);
                    chatResponse.setReply("💚 **Psychiatrist & Emotional Support Assistant** (Specialist Therapy Session):\n\n" + psychReply);
                } catch (Exception e) {
                    log.error("Failed to connect to psychiatry chatbot", e);
                    chatResponse.setReply("💚 **Mental Health Support**\n\n" +
                            "I tried to contact our specialist Psychiatric bot but it seems offline. However, please know that you are not alone:\n" +
                            "📞 **National Mental Health Helpline (KIRAN)**: 1800-599-0019 (24/7 free counseling)\n\n" +
                            "Please call them for professional help or check back shortly when the bot is active.");
                }
                chatResponse.setState("IDLE");
                break;

            case "raise_issue":
            default:
                // Citizens raising a civic issue
                String desc = extract.getDescription() != null ? extract.getDescription() : message;
                String category = extract.getCategory() != null ? extract.getCategory() : "General Protest Issue";
                String locName = extract.getLocation() != null ? extract.getLocation() : "Current Coordinates";

                // Resolve politician
                Map<String, GeoBoundary> boundaryMap = spatialResolutionService.resolvePoliticalHierarchy(latitude, longitude);
                GeoBoundary ward = boundaryMap.get("ward");
                String wardCode = "WARD-01"; // Fallback to seeded Ward
                if (ward != null) {
                    wardCode = ward.getCode();
                }

                Optional<Candidate> politicianOpt = candidateRepository.findByBoundaryCode(wardCode);
                String politicianName = politicianOpt.map(Candidate::getName).orElse("your Ward representative");

                // 4. Duplicate Issue Checking
                List<Issue> activeIssuesInWard = issueRepository.findByWardCodeAndStatusAndHiddenFalse(wardCode, "SUBMITTED");
                activeIssuesInWard.addAll(issueRepository.findByWardCodeAndStatusAndHiddenFalse(wardCode, "APPROVED"));

                List<DuplicateIssueDto> duplicates = findDuplicates(desc, activeIssuesInWard);
                chatResponse.setDuplicateIssues(duplicates);

                if (!duplicates.isEmpty() && duplicates.get(0).getSimilarityScore() >= 0.75) {
                    // Match found! Direct user to the existing issue
                    DuplicateIssueDto topMatch = duplicates.get(0);
                    chatResponse.setReply("🔍 **Duplicate Issue Detected!**\n\n" +
                            "A highly similar issue has already been reported in your Ward:\n" +
                            "📌 **" + topMatch.getTitle() + "** (`" + topMatch.getReadableIssueId() + "`)\n" +
                            "📝 *\"" + topMatch.getDescription() + "\"*\n" +
                            "💡 *Similarity score: " + String.format("%.1f%%", topMatch.getSimilarityScore() * 100) + "*\n\n" +
                            "To prevent clutter, please click on this issue in the feed to **Like (Upvote)** or **Comment (Add feedback)** instead of raising a new ticket!");
                    chatResponse.setState("IDLE");
                } else {
                    // Genuine new issue: set up confirmation flow
                    session.setState("AWAITING_CONFIRMATION");
                    session.setTitle(category + " report at " + locName);
                    session.setDescription(desc);
                    session.setCategory(category);
                    session.setLocation(locName);
                    session.setLatitude(latitude);
                    session.setLongitude(longitude);
                    session.setUrgency(extract.getUrgency());
                    session.setFrustrationLevel(extract.getFrustrationLevel());
                    session.setPoliticianName(politicianName);

                    String frustrationNotice = "";
                    if (extract.getFrustrationLevel() >= 7) {
                        frustrationNotice = "I sense your high level of frustration (rated " + extract.getFrustrationLevel() + "/10) regarding this problem. ";
                    }

                    chatResponse.setReply("🤖 **Protest Issue Triage Report**\n\n" +
                            "Thank you for reporting this. I have extracted the following details:\n" +
                            "📌 **Category**: " + category + "\n" +
                            "📍 **Location**: " + locName + " (" + latitude + ", " + longitude + ")\n" +
                            "📝 **Description**: \"" + desc + "\"\n" +
                            "⚠️ **Urgency**: " + extract.getUrgency() + "\n\n" +
                            frustrationNotice + "Would you like me to submit this to your local approver **" + politicianName + "**? Please reply with **Yes** or **No**.");
                    chatResponse.setState("AWAITING_CONFIRMATION");
                }
                break;
        }

        return chatResponse;
    }

    private LlmExtractionResult callLlmOrchestrator(String userMessage) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(hfToken);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "Qwen/Qwen2.5-7B-Instruct");
        body.put("temperature", 0.1);
        body.put("max_tokens", 256);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userMessage));
        body.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(hfModelUrl, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List choices = (List) response.getBody().get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map msg = (Map) firstChoice.get("message");
            String content = ((String) msg.get("content")).trim();

            log.info("LLM Raw Response: {}", content);

            // Clean JSON formatting from markdown if model wraps it
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7);
                content = content.substring(0, content.indexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3);
                content = content.substring(0, content.indexOf("```"));
            }
            content = content.trim();

            return objectMapper.readValue(content, LlmExtractionResult.class);
        }

        throw new IllegalStateException("Failed to call HuggingFace API: status " + response.getStatusCode());
    }

    private LlmExtractionResult getFallbackExtraction(String userMessage) {
        LlmExtractionResult extract = new LlmExtractionResult();
        String msgLower = userMessage.toLowerCase();

        if (msgLower.contains("police") || msgLower.contains("thief") || msgLower.contains("crime") || msgLower.contains("robberry")) {
            extract.setIntent("police");
            extract.setUrgency("Critical");
            extract.setFrustrationLevel(9);
        } else if (msgLower.contains("hospital") || msgLower.contains("accident") || msgLower.contains("injury") || msgLower.contains("medical")) {
            extract.setIntent("hospital");
            extract.setUrgency("Critical");
            extract.setFrustrationLevel(9);
        } else if (msgLower.contains("depression") || msgLower.contains("sad") || msgLower.contains("hopeless") || msgLower.contains("crying") || msgLower.contains("suicide") || msgLower.contains("mental")) {
            extract.setIntent("psychiatric_help");
            extract.setUrgency("High");
            extract.setFrustrationLevel(8);
        } else if (msgLower.contains("ngo") || msgLower.contains("volunteer") || msgLower.contains("private support")) {
            extract.setIntent("ngo");
            extract.setUrgency("Low");
            extract.setFrustrationLevel(2);
        } else {
            extract.setIntent("raise_issue");
            extract.setCategory("General Issue");
            extract.setLocation("Local Area");
            extract.setDescription(userMessage);
            extract.setUrgency("Medium");
            extract.setFrustrationLevel(5);
        }

        return extract;
    }

    private String callPsychiatryBot(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("message", userMessage);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(psychiatryUrl, entity, Map.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return (String) response.getBody().get("response");
        }
        return "Sorry, I had trouble communicating with the psychiatry database.";
    }

    private List<DuplicateIssueDto> findDuplicates(String newDesc, List<Issue> existingIssues) {
        List<DuplicateIssueDto> duplicates = new ArrayList<>();
        for (Issue issue : existingIssues) {
            double sim = calculateTextSimilarity(newDesc, issue.getDescription());
            if (sim >= 0.5) { // Any matching threshold over 50%
                DuplicateIssueDto dto = new DuplicateIssueDto();
                dto.setReadableIssueId(issue.getReadableIssueId());
                dto.setTitle(issue.getTitle());
                dto.setDescription(issue.getDescription());
                dto.setSimilarityScore(sim);
                duplicates.add(dto);
            }
        }
        duplicates.sort((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()));
        return duplicates;
    }

    // Levenshtein & Jaccard Hybrid Similarity metric
    private double calculateTextSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        s1 = s1.toLowerCase().replaceAll("[^a-zA-Z0-9 ]", " ").trim();
        s2 = s2.toLowerCase().replaceAll("[^a-zA-Z0-9 ]", " ").trim();

        if (s1.equals(s2)) return 1.0;

        // 1. Jaccard Index (Word Token Intersection)
        Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        double jaccard = (double) intersection.size() / union.size();

        // 2. Levenshtein Distance
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1])) + 1;
                }
            }
        }
        double levenshtein = (double) (Math.max(len1, len2) - dp[len1][len2]) / Math.max(len1, len2);

        // Weighted Average: 70% Jaccard (focus on terms) + 30% Levenshtein (focus on character sequence)
        return (0.7 * jaccard) + (0.3 * levenshtein);
    }
}
