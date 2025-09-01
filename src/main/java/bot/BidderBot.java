package bot;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BidderBot {
    // Order data structure for intelligent bidding
    private static class OrderDetails {
        String url;
        String title;
        String category;
        String description;
        int bidCount;
        LocalDateTime deadline;
        boolean hasFiles;
        boolean customerOnline;
        boolean priceSet;
        String budgetInfo;
        int estimatedPages;
        boolean isUrgent;
        
        OrderDetails(String url) {
            this.url = url;
        }
    }
    // Subject expertise and message templates
    private final Map<String, List<String>> subjectTemplates = new HashMap<>();
    private final Map<String, String> expertiseMap = new HashMap<>();
    private final List<String> urgencyKeywords = Arrays.asList(
        "urgent", "asap", "rush", "emergency", "immediately", "quick", "fast"
    );
    
    private final String username;
    private final String password;
    private final String bidText;
    private final BidderApp app;
    
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    
    private volatile boolean running = false;
    private int foundOrders = 0;
    private int successfulBids = 0;
    private Set<String> processedOrders = new HashSet<>();
    private int pollingDots = 0;
    
    // AGGRESSIVE BIDDING STRATEGY CONFIGURATION
    private int scanList = 1;
    private int fullScanInterval = 10;
    private int fullScanDepthLimit = 3;
    private int currentCycle = 0;
    private int refreshRate = 3; // seconds - FASTER for competitive bidding
    // Removed: accumulatedOrders - now processing directly from search page
    
    // Simplified filter exploitation - no filter changes, just apply button triggering
    
    // Configuration
    private static final String BASE_URL = "https://studybay.com";
    private static final String LOGIN_URL = BASE_URL + "/login";
    private static final String ORDERS_URL = BASE_URL + "/order/search";
    private static final String STORAGE_STATE_PATH = "session.json";
    
    // Selectors
    private static final String USERNAME_SELECTOR = "input[name='email'], input[type='email']";
    private static final String PASSWORD_SELECTOR = "input[name='password'], input[type='password']";
    private static final String LOGIN_BUTTON_SELECTOR = "button[type='submit'], input[type='submit']";
    private static final String ORDER_LINK_SELECTOR = ".orderA-converted__name";
    private static final String BID_BUTTON_SELECTOR = "button.styled__MakeBidButton-sc-18augvm-9, button[data-testid*='MakeBid'], button:has-text('Place a Bid')";
    private static final String BID_TEXT_SELECTOR = ".auctionTextarea-converted__textarea, textarea[name='message'], textarea[placeholder*='bid']";
    private static final String SUBMIT_BID_SELECTOR = "button[type='submit']:has-text('Submit'), button:has-text('Send Bid'), button.styled__StyledButton-sc-6klmhm-0";
    
    // AJAX FILTER EXPLOITATION SELECTORS
    private static final String FILTER_APPLY_SELECTOR = ".filter-converted__apply";
    private static final String ORDER_CONTAINER_SELECTOR = "div.orderA-converted__contentWrapper";
    
    public BidderBot(String username, String password, String bidText, BidderApp app) {
        this.username = username;
        this.password = password;
        this.bidText = bidText;
        this.app = app;
        initializeSubjectTemplates();
        initializeExpertiseMap();
    }
    
    private void initializeSubjectTemplates() {
        // Essay and Writing templates
        subjectTemplates.put("essay", Arrays.asList(
            "Hi! I'm an experienced essay writer with expertise in {subject}. I can deliver a well-structured, compelling essay that meets all your requirements.",
            "Hello! I specialize in essay writing and have successfully completed similar projects. I'll ensure your essay is engaging, well-researched, and properly formatted.",
            "Greetings! As a professional writer, I can craft an excellent essay that demonstrates critical thinking and meets academic standards."
        ));
        
        // Research and Academic templates
        subjectTemplates.put("research", Arrays.asList(
            "Hi! I have extensive research experience in {subject}. I'll provide you with a thoroughly researched, well-documented paper with credible sources.",
            "Hello! I'm skilled in academic research and can deliver a comprehensive analysis with proper citations and methodology.",
            "Greetings! My research expertise allows me to provide in-depth analysis and evidence-based conclusions for your project."
        ));
        
        // Art and Design templates
        subjectTemplates.put("art", Arrays.asList(
            "Hi! I have a strong background in art and design. I can provide creative insights and professional analysis for your project.",
            "Hello! As someone passionate about art, I can deliver thoughtful commentary and expert analysis on artistic concepts.",
            "Greetings! My experience in the arts allows me to provide both creative and analytical perspectives on your assignment."
        ));
        
        // Business and Economics templates
        subjectTemplates.put("business", Arrays.asList(
            "Hi! I have business expertise and can provide strategic analysis, market insights, and professional recommendations.",
            "Hello! My business background enables me to deliver practical solutions and industry-relevant analysis.",
            "Greetings! I can apply business principles and real-world experience to create a comprehensive project."
        ));
        
        // Science and Technical templates
        subjectTemplates.put("science", Arrays.asList(
            "Hi! I have a strong scientific background and can explain complex concepts clearly while maintaining accuracy.",
            "Hello! My technical expertise allows me to provide detailed analysis and evidence-based conclusions.",
            "Greetings! I can combine scientific rigor with clear communication to deliver excellent results."
        ));
        
        // Mathematics templates
        subjectTemplates.put("math", Arrays.asList(
            "Hi! I'm proficient in mathematics and can provide step-by-step solutions with clear explanations.",
            "Hello! My mathematical background ensures accurate calculations and detailed problem-solving approaches.",
            "Greetings! I can handle complex mathematical concepts and present solutions in an understandable format."
        ));
        
        // General/Default templates
        subjectTemplates.put("general", Arrays.asList(
            "Hi! I'm an experienced academic writer who can adapt to various subjects and deliver high-quality work.",
            "Hello! I have a versatile background that allows me to tackle diverse topics with professionalism and expertise.",
            "Greetings! My broad knowledge base and strong writing skills make me well-suited for your project."
        ));
    }
    
    private void initializeExpertiseMap() {
        expertiseMap.put("essay", "5+ years of essay writing experience");
        expertiseMap.put("research", "extensive research and analysis background");
        expertiseMap.put("art", "strong foundation in arts and creative analysis");
        expertiseMap.put("business", "professional business and management experience");
        expertiseMap.put("science", "solid scientific and technical background");
        expertiseMap.put("math", "advanced mathematical and analytical skills");
        expertiseMap.put("literature", "deep understanding of literary analysis and criticism");
        expertiseMap.put("history", "comprehensive knowledge of historical contexts and analysis");
        expertiseMap.put("psychology", "expertise in psychological theories and research methods");
        expertiseMap.put("general", "diverse academic background and proven writing abilities");
    }
    
    public void start() throws Exception {
        running = true;
        app.logMessage("Initializing Playwright...");
        
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(false)
            .setSlowMo(1000));
        
        // Create context with session persistence
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .setViewportSize(1280, 800);
        
        Path sessionPath = Path.of(STORAGE_STATE_PATH);
        if (Files.exists(sessionPath)) {
            contextOptions.setStorageStatePath(sessionPath);
            app.logMessage("Loading existing session...");
        }
        
        context = browser.newContext(contextOptions);
        page = context.newPage();
        
        // Set timeouts
        page.setDefaultTimeout(30000);
        page.setDefaultNavigationTimeout(60000);
        
        // Login if needed
        if (!Files.exists(sessionPath) || !isLoggedIn()) {
            login();
        } else {
            app.logMessage("Using existing session");
        }
        
        // Start monitoring orders
        monitorOrders();
    }
    
    private boolean isLoggedIn() {
        try {
            // First check current URL without navigation
            String currentUrl = page.url();
            
            // If we're already on orders page, check for login indicators
            if (currentUrl.contains("/order/search") || currentUrl.contains("/orders")) {
                // Check if page has login form (indicates not logged in)
                try {
                    page.locator(USERNAME_SELECTOR + ", " + PASSWORD_SELECTOR).first().waitFor(new Locator.WaitForOptions().setTimeout(2000));
                    return false; // Found login form, not logged in
                } catch (Exception e) {
                    // No login form found, likely logged in
                    return true;
                }
            }
            
            // Navigate to orders page to test login status
            page.navigate(ORDERS_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            Thread.sleep(2000);
            
            // Check if we're redirected to login page
            currentUrl = page.url();
            if (currentUrl.contains("login") || currentUrl.contains("signin")) {
                return false;
            }
            
            // Check if we can see order-related content (indicates logged in)
            try {
                page.locator(".order, .orderA, [class*='order'], h1, .search-form").first().waitFor(new Locator.WaitForOptions().setTimeout(3000));
                return true;
            } catch (Exception e) {
                // Check for login form as fallback
                try {
                    page.locator(USERNAME_SELECTOR + ", " + PASSWORD_SELECTOR).first().waitFor(new Locator.WaitForOptions().setTimeout(2000));
                    return false; // Found login form
                } catch (Exception ex) {
                    return true; // No login form, assume logged in
                }
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    private void login() throws Exception {
        app.logMessage("Navigating to login page...");
        page.navigate(LOGIN_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        
        try {
            // Auto-login attempt
            app.logMessage("Attempting auto-login...");
            
            Locator usernameField = page.locator(USERNAME_SELECTOR).first();
            usernameField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            usernameField.fill(username);
            
            Locator passwordField = page.locator(PASSWORD_SELECTOR).first();
            passwordField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
            passwordField.fill(password);
            
            Locator loginButton = page.locator(LOGIN_BUTTON_SELECTOR).first();
            loginButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
            loginButton.click();
            
            // Wait for navigation after login
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30000));
            
            // Verify login success
            Thread.sleep(3000);
            if (isLoggedIn()) {
                app.logMessage("Auto-login successful!");
                // Save session
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Path.of(STORAGE_STATE_PATH)));
                app.logMessage("Session saved for future use");
            } else {
                throw new RuntimeException("Auto-login failed");
            }
            
        } catch (Exception e) {
            app.logMessage("Auto-login failed: " + e.getMessage());
            app.logMessage("Please login manually in the browser window, then the bot will continue...");
            
            // Wait for manual login
            waitForManualLogin();
        }
    }
    
    private void waitForManualLogin() throws Exception {
        app.logMessage("Waiting for manual login...");
        
        // Wait until we detect successful login
        while (running && !isLoggedIn()) {
            Thread.sleep(2000);
        }
        
        if (running) {
            app.logMessage("Manual login detected!");
            // Save session
            context.storageState(new BrowserContext.StorageStateOptions().setPath(Path.of(STORAGE_STATE_PATH)));
            app.logMessage("Session saved for future use");
        }
    }
    
    private void monitorOrders() throws Exception {
        app.logMessage("ULTRA-FAST INTELLIGENT BIDDING INITIATED - Search Page Direct Processing");
        
        // Initial navigation only - NO MORE PAGE RELOADS
        page.navigate(ORDERS_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        Thread.sleep(2000);
        app.logMessage("Search page loaded - SPEED MODE: Processing orders directly from containers");
        
        while (running) {
            try {
                currentCycle++;
                showPollingMessage();
                
                // CORE EXPLOITATION: Trigger predefined filters via AJAX (NO PAGE RELOADS)
                triggerAJAXFilterApplication();
                
                // DOM expansion and accumulation
                expandDOMThroughScrolling();
                
                // Harvest all accumulated orders from DOM
                // REMOVED: harvestAccumulatedOrders() - now processing directly
                
                // Human-like timing with randomization (FASTER for competition)
                int delay = refreshRate + ThreadLocalRandom.current().nextInt(1, 4); // Reduced delay
                Thread.sleep(delay * 1000);
                
            } catch (Exception e) {
                Thread.sleep(5000);
            }
        }
    }
    
    private void showPollingMessage() {
        pollingDots = (pollingDots + 1) % 4; // Cycle through 0, 1, 2, 3
        String dots = ".".repeat(pollingDots);
        String message = "SPEED SCAN" + dots + " ".repeat(3 - pollingDots); // Fast scanning indicator
        app.logMessage(message);
    }
    


    
    private boolean containsUrgencyKeywords(String text) {
        if (text == null) return false;
        String lowerText = text.toLowerCase();
        return urgencyKeywords.stream().anyMatch(lowerText::contains);
    }
    
    private int extractPageCount(String description) {
        if (description == null) return 0;
        
        // Look for page/word count patterns
        Pattern pagePattern = Pattern.compile("(\\d+)\\s*(?:pages?|words?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pagePattern.matcher(description);
        
        if (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            // Convert words to estimated pages (assuming ~250 words per page)
            if (description.toLowerCase().contains("word")) {
                return (int) Math.ceil(number / 250.0);
            }
            return number;
        }
        return 0;
    }
    
    private int extractNumberFromText(String text) {
        if (text == null) return 0;
        Pattern numberPattern = Pattern.compile("(\\d+)");
        Matcher matcher = numberPattern.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
    
    private LocalDateTime parseDeadline(String deadlineText) {
        if (deadlineText == null) return null;
        
        try {
            // Handle various date formats
            if (deadlineText.contains(",")) {
                // Format: "September 08, 2025, 02:03 am"
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy, hh:mm a");
                return LocalDateTime.parse(deadlineText.trim(), formatter);
            }
        } catch (Exception e) {
            // Fallback: assume 7 days from now if parsing fails
            return LocalDateTime.now().plusDays(7);
        }
        
        return LocalDateTime.now().plusDays(7);
    }
    
    // SIMPLIFIED AJAX FILTER EXPLOITATION METHOD
    
    private void triggerAJAXFilterApplication() {
        try {
            // CORE EXPLOITATION: Click predefined filter apply button to trigger AJAX
            if (page.locator(FILTER_APPLY_SELECTOR).count() > 0) {
                page.locator(FILTER_APPLY_SELECTOR).first().click();
                Thread.sleep(ThreadLocalRandom.current().nextInt(1500, 3000));
                
                // Wait for AJAX response and DOM update (NO PAGE RELOAD)
                page.waitForLoadState(LoadState.NETWORKIDLE);
                app.logMessage("AJAX filter applied - DOM refreshed with new orders");
                
                // Immediately collect orders after AJAX response
                collectOrdersFromCurrentDOM();
            }
        } catch (Exception e) {
            // Silent AJAX trigger errors
        }
    }
    
    private void expandDOMThroughScrolling() {
        try {
            // Scroll manipulation to trigger more content loading
            page.evaluate("window.scroll(0, -250);"); // Scroll up
            Thread.sleep(500);
            page.evaluate("window.scroll(0, document.body.scrollHeight);"); // Scroll down
            Thread.sleep(500);
            page.evaluate("window.scroll(0, 0);"); // Back to top
            Thread.sleep(1000);
        } catch (Exception e) {
            // Silent scroll errors
        }
    }
    
    private void collectOrdersFromCurrentDOM() {
        try {
            // Harvest ALL orders from current DOM state (post-AJAX update)
            Locator orderContainers = page.locator(".orderA-converted__order");
            int orderCount = orderContainers.count();
            
            app.logMessage("Found " + orderCount + " order containers on search page");
            
            for (int i = 0; i < orderCount; i++) {
                try {
                    Locator container = orderContainers.nth(i);
                    
                    // Extract order URL for tracking (but don't navigate)
                    Locator linkElement = container.locator(".orderA-converted__name");
                    if (linkElement.count() > 0) {
                        String href = linkElement.getAttribute("href");
                        if (href != null && !href.isEmpty()) {
                            String fullUrl = BASE_URL + href;
                            
                            // Check if we've already processed this order
                            if (!processedOrders.contains(fullUrl)) {
                                // Process order directly from search page container
                                processOrderFromSearchPage(container, fullUrl);
                                processedOrders.add(fullUrl);
                                
                                // Small delay between orders for human-like behavior
                                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1200));
                            }
                        }
                    }
                } catch (Exception e) {
                    // Silent individual order processing errors
                }
            }
        } catch (Exception e) {
            // Silent DOM collection errors
        }
    }
    
    private void processOrderFromSearchPage(Locator orderContainer, String orderUrl) {
        try {
            // FAST: Extract order details directly from search page container
            OrderDetails orderDetails = extractOrderDetailsFromContainer(orderContainer, orderUrl);
            
            // Quick intelligence check - skip obviously bad orders
            if (!shouldProcessOrder(orderDetails)) {
                app.logMessage("Skipped order: " + orderDetails.title + " (" + getSkipReason(orderDetails) + ")");
                return;
            }
            
            // Generate intelligent message BEFORE clicking (preparation)
            String intelligentMessage = generateIntelligentBidMessage(orderDetails);
            
            // SPEED CRITICAL: Find and click "Place a Bid" button in this container
            Locator bidButton = orderContainer.locator(BID_BUTTON_SELECTOR);
            if (bidButton.count() > 0) {
                // Log the intelligent analysis
                app.logMessage("FAST BID: " + orderDetails.title + " (" + orderDetails.bidCount + " bids, " + 
                    (orderDetails.customerOnline ? "online" : "offline") + ")");
                
                // Click to open bid form (modal on same page)
                bidButton.first().click();
                Thread.sleep(ThreadLocalRandom.current().nextInt(300, 700)); // Minimal delay
                
                // RAPID: Fill and submit bid form
                if (submitBidFormFast(intelligentMessage)) {
                    successfulBids++;
                    app.updateSuccessfulBids(successfulBids);
                    foundOrders++;
                    app.updateFoundOrders(foundOrders);
                    app.logMessage("✓ FAST BID PLACED: " + orderDetails.title);
                } else {
                    app.logMessage("✗ Fast bid failed: " + orderDetails.title);
                }
            } else {
                app.logMessage("No bid button found in container for: " + orderDetails.title);
            }
            
        } catch (Exception e) {
            app.logMessage("Error in fast order processing: " + e.getMessage());
        }
    }
    
    private OrderDetails extractOrderDetailsFromContainer(Locator container, String orderUrl) {
        OrderDetails details = new OrderDetails(orderUrl);
        
        try {
            // FAST: Extract from search page container only
            
            // Title from order link
            Locator titleElement = container.locator(".orderA-converted__name");
            if (titleElement.count() > 0) {
                details.title = titleElement.textContent().trim();
            }
            
            // Category
            Locator categoryElement = container.locator(".orderA-converted__category");
            if (categoryElement.count() > 0) {
                details.category = categoryElement.textContent().trim();
            }
            
            // Bid count
            Locator bidCountElement = container.locator(".orderA-converted__bidsCounter");
            if (bidCountElement.count() > 0) {
                String bidText = bidCountElement.textContent().trim();
                details.bidCount = extractNumberFromText(bidText);
            }
            
            // Deadline
            Locator deadlineElement = container.locator(".base__DeadlineDateRoot-sc-1rxnbg2-3");
            if (deadlineElement.count() > 0) {
                String deadlineText = deadlineElement.textContent().trim();
                details.deadline = parseDeadline(deadlineText);
                details.isUrgent = isDeadlineTight(details.deadline);
            }
            
            // Customer online status
            details.customerOnline = container.locator("[class*='online']").count() > 0;
            
            // Files availability
            details.hasFiles = container.locator("[class*='file'], [class*='Files']").count() > 0;
            
            // Budget info
            Locator budgetElement = container.locator(".core__OfferRoot-sc-p84bdz-0");
            if (budgetElement.count() > 0) {
                details.budgetInfo = budgetElement.textContent().trim();
                details.priceSet = !details.budgetInfo.toLowerCase().contains("not set");
            }
            
            // Quick urgency check from visible text
            String containerText = container.textContent().toLowerCase();
            details.isUrgent = details.isUrgent || containsUrgencyKeywords(containerText);
            
        } catch (Exception e) {
            // Continue with partial data
        }
        
        return details;
    }
    
    private boolean shouldProcessOrder(OrderDetails order) {
        // FAST filtering - skip obviously bad orders
        if (order.bidCount > 25) return false; // Too much competition
        if (order.title == null || order.title.length() < 5) return false; // Invalid title
        return true; // Process most orders for speed, detailed filtering later
    }
    
    private String getSkipReason(OrderDetails order) {
        if (order.bidCount > 25) return "high competition: " + order.bidCount + " bids";
        if (order.title == null || order.title.length() < 5) return "invalid title";
        return "unknown";
    }
    
    private boolean submitBidFormFast(String bidMessage) {
        try {
            // SPEED CRITICAL: Handle bid form modal with minimal delays
            
            // Wait for bid form to appear (should be instant modal)
            Locator bidForm = page.locator(".ui-modal-content, .sb-makeOffer-converted__bid");
            bidForm.first().waitFor(new Locator.WaitForOptions().setTimeout(3000));
            
            // Fill text area rapidly
            Locator textArea = page.locator(".auctionTextarea-converted__textarea");
            if (textArea.count() > 0) {
                textArea.first().fill(bidMessage);
                Thread.sleep(ThreadLocalRandom.current().nextInt(200, 500)); // Minimal delay
                
                // Find and click submit button (wait for it to be enabled)
                Locator submitButton = page.locator("button:has-text('Place a Bid'):not([disabled])");
                
                // Wait briefly for button to become enabled after text entry
                try {
                    submitButton.first().waitFor(new Locator.WaitForOptions().setTimeout(2000));
                    submitButton.first().click();
                    
                    // Brief wait to verify submission
                    Thread.sleep(ThreadLocalRandom.current().nextInt(300, 800));
                    
                    // Check if modal disappeared (success indicator)
                    boolean modalGone = page.locator(".ui-modal-content").count() == 0;
                    
                    return modalGone;
                    
                } catch (Exception e) {
                    // Button didn't become enabled, might need price input
                    // For now, click cancel to close modal
                    Locator cancelButton = page.locator("button:has-text('Cancel')");
                    if (cancelButton.count() > 0) {
                        cancelButton.first().click();
                    }
                    return false;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            // Try to close any open modal
            try {
                Locator closeButton = page.locator(".ui-modal-close, button:has-text('Cancel')");
                if (closeButton.count() > 0) {
                    closeButton.first().click();
                }
            } catch (Exception ignored) {}
            return false;
        }
    }
    

    
    private String generateIntelligentBidMessage(OrderDetails order) {
        StringBuilder message = new StringBuilder();
        
        // Step 1: Get subject-specific template
        String subjectKey = determineSubjectKey(order.category, order.title, order.description);
        List<String> templates = subjectTemplates.getOrDefault(subjectKey, subjectTemplates.get("general"));
        String baseTemplate = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        
        // Step 2: Replace subject placeholder with actual subject
        String subjectName = extractSubjectName(order.category, order.title);
        baseTemplate = baseTemplate.replace("{subject}", subjectName);
        
        message.append(baseTemplate);
        
        // Step 3: Add urgency-aware messaging
        if (order.isUrgent || isDeadlineTight(order.deadline)) {
            message.append(" I understand the urgency of your request and am available to start immediately.");
        }
        
        // Step 4: Add competition-aware value propositions
        if (order.bidCount > 10) {
            message.append(" With many proposals submitted, I focus on delivering exceptional quality that stands out.");
        } else if (order.bidCount < 5) {
            message.append(" I'm excited to be among the first to offer my expertise for this project.");
        }
        
        // Step 5: Add customer behavior-based personalization
        if (order.customerOnline) {
            message.append(" I see you're currently online - I'm available for immediate discussion about your requirements.");
        }
        
        // Step 6: Add deadline commitment strategy
        if (order.deadline != null) {
            long hoursUntilDeadline = ChronoUnit.HOURS.between(LocalDateTime.now(), order.deadline);
            if (hoursUntilDeadline <= 48) {
                message.append(" I can prioritize your project to meet the tight deadline.");
            } else if (hoursUntilDeadline <= 168) { // 1 week
                message.append(" I have the availability to deliver well before your deadline.");
            }
        }
        
        // Step 7: Add file handling if applicable
        if (order.hasFiles) {
            message.append(" I've noted the attached files and will review them thoroughly to ensure all requirements are met.");
        }
        
        // Step 8: Add experience level matching
        String expertise = expertiseMap.getOrDefault(subjectKey, expertiseMap.get("general"));
        message.append(" My ").append(expertise).append(" ensures high-quality results.");
        
        // Step 9: Add unique selling points based on order complexity
        if (order.estimatedPages > 10) {
            message.append(" I'm comfortable handling comprehensive projects and excel at organizing complex content.");
        } else if (order.estimatedPages > 0 && order.estimatedPages <= 5) {
            message.append(" I can deliver concise, impactful work that maximizes value within the scope.");
        }
        
        // Step 10: Professional closing with call to action
        if (order.customerOnline) {
            message.append(" Let's connect now to discuss your specific needs!");
        } else {
            message.append(" I look forward to discussing how I can help achieve your goals.");
        }
        
        return message.toString();
    }
    
    private String determineSubjectKey(String category, String title, String description) {
        String combinedText = (category + " " + title + " " + description).toLowerCase();
        
        // Priority matching for specific subjects
        if (combinedText.contains("essay") || combinedText.contains("writing")) return "essay";
        if (combinedText.contains("research") || combinedText.contains("analysis")) return "research";
        if (combinedText.contains("art") || combinedText.contains("design")) return "art";
        if (combinedText.contains("business") || combinedText.contains("management") || combinedText.contains("marketing")) return "business";
        if (combinedText.contains("science") || combinedText.contains("biology") || combinedText.contains("chemistry") || combinedText.contains("physics")) return "science";
        if (combinedText.contains("math") || combinedText.contains("calculus") || combinedText.contains("statistics")) return "math";
        if (combinedText.contains("literature") || combinedText.contains("english")) return "literature";
        if (combinedText.contains("history") || combinedText.contains("historical")) return "history";
        if (combinedText.contains("psychology") || combinedText.contains("psych")) return "psychology";
        
        return "general";
    }
    
    private String extractSubjectName(String category, String title) {
        if (category != null && !category.isEmpty()) {
            // Clean up category name
            return category.replaceAll("Q&A,?\\s*", "").trim();
        }
        if (title != null && !title.isEmpty()) {
            return title;
        }
        return "this subject";
    }
    
    private boolean isDeadlineTight(LocalDateTime deadline) {
        if (deadline == null) return false;
        long hoursUntilDeadline = ChronoUnit.HOURS.between(LocalDateTime.now(), deadline);
        return hoursUntilDeadline <= 48; // Less than 48 hours is considered tight
    }
    

    
    public void stop() {
        running = false;
        
        try {
            if (context != null) {
                // Save session before closing
                context.storageState(new BrowserContext.StorageStateOptions().setPath(Path.of(STORAGE_STATE_PATH)));
                context.close();
            }
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        
        app.logMessage("Bot stopped and resources cleaned up");
    }
}
