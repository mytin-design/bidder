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
    
    // ULTRA-AGGRESSIVE BIDDING STRATEGY CONFIGURATION
    private int scanList = 1;
    private int fullScanInterval = 10;
    private int fullScanDepthLimit = 3;
    private int currentCycle = 0;
    private int refreshRate = 0; // INSTANT - NO DELAYS for competitive bidding
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
            System.out.println("Current URL: " + currentUrl); // Debug
            
            // If we're already on orders page, check for login indicators
            if (currentUrl.contains("/order/search") || currentUrl.contains("/orders")) {
                // Check if page has login form (indicates not logged in)
                try {
                    page.locator(USERNAME_SELECTOR + ", " + PASSWORD_SELECTOR).first().waitFor(new Locator.WaitForOptions().setTimeout(2000));
                    System.out.println("Found login form on orders page - not logged in");
                    return false; // Found login form, not logged in
                } catch (Exception e) {
                    // No login form found, check for order content
                    try {
                        page.locator(".order, .orderA, [class*='order'], .search-form").first().waitFor(new Locator.WaitForOptions().setTimeout(3000));
                        System.out.println("Found order content - logged in");
                        return true;
                    } catch (Exception ex) {
                        System.out.println("No order content found - assuming not logged in");
                        return false;
                    }
                }
            }
            
            // If not on orders page, need to navigate once to test
            System.out.println("Not on orders page, navigating to test login status...");
            page.navigate(ORDERS_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            Thread.sleep(3000); // Give more time for potential redirects
            
            // Check final URL after navigation
            currentUrl = page.url();
            System.out.println("After navigation, URL: " + currentUrl);
            
            if (currentUrl.contains("login") || currentUrl.contains("signin")) {
                System.out.println("Redirected to login page - not logged in");
                return false;
            }
            
            // Check if we can see order-related content (indicates logged in)
            try {
                page.locator(".order, .orderA, [class*='order'], h1, .search-form").first().waitFor(new Locator.WaitForOptions().setTimeout(5000));
                System.out.println("Found order content after navigation - logged in");
                return true;
            } catch (Exception e) {
                // Final check for login form
                try {
                    page.locator(USERNAME_SELECTOR + ", " + PASSWORD_SELECTOR).first().waitFor(new Locator.WaitForOptions().setTimeout(2000));
                    System.out.println("Found login form after navigation - not logged in");
                    return false; // Found login form
                } catch (Exception ex) {
                    System.out.println("No login form found, assuming logged in");
                    return true; // No login form, assume logged in
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in isLoggedIn: " + e.getMessage());
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
        app.logMessage("started"); // Trigger animation start
        
        // Ensure we're on the search page and stay there
        String currentUrl = page.url();
        if (!currentUrl.contains("/order/search")) {
            System.out.println("Not on search page, navigating: " + currentUrl);
            page.navigate(ORDERS_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            Thread.sleep(3000);
        }
        
        System.out.println("Starting monitoring loop on: " + page.url());
        
        while (running) {
            try {
                currentCycle++;
                
                // Check if we're still on the right page
                currentUrl = page.url();
                if (!currentUrl.contains("/order/search") && !currentUrl.contains("/orders")) {
                    // Navigate back to search page instantly
                    page.navigate(ORDERS_URL);
                    Thread.sleep(1000); // Quick wait for page load
                    continue;
                }
                
                // ULTRA-FAST: Trigger filters and collect orders instantly
                triggerAJAXFilterApplication();
                
                // Quick DOM expansion
                expandDOMThroughScrolling();
                
                // EXPERIMENTAL: Try parallel bid placement every 10 cycles
                if (currentCycle % 10 == 0) {
                    tryParallelBidPlacement();
                }
                
                // ULTRA-FAST timing - minimal delays for instant capture
                Thread.sleep(100); // Only 100ms delay between cycles
                
            } catch (Exception e) {
                System.out.println("Error in monitoring loop: " + e.getMessage());
                Thread.sleep(500); // Quick recovery
            }
        }
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
            // ULTRA-FAST: Click filter button INSTANTLY - no delays
            if (page.locator(FILTER_APPLY_SELECTOR).count() > 0) {
                page.locator(FILTER_APPLY_SELECTOR).first().click();
                Thread.sleep(50); // Only 50ms wait for AJAX
                
                // Immediately collect orders after minimal wait
                collectOrdersFromCurrentDOM();
            } else {
                // If no filter button, directly collect orders
                collectOrdersFromCurrentDOM();
            }
        } catch (Exception e) {
            // Continue regardless of filter errors
            collectOrdersFromCurrentDOM();
        }
    }
    
    private void expandDOMThroughScrolling() {
        try {
            // ULTRA-FAST scrolling - minimal delays
            page.evaluate("window.scroll(0, document.body.scrollHeight);"); // Quick scroll
            Thread.sleep(50); // Only 50ms
        } catch (Exception e) {
            // Silent scroll errors
        }
    }
    
    private void collectOrdersFromCurrentDOM() {
        try {
            // ULTRA-FAST: Harvest ALL orders instantly
            Locator orderContainers = page.locator(".orderA-converted__order");
            int orderCount = orderContainers.count();
            
            if (orderCount > 0) {
                foundOrders += orderCount;
                app.updateFoundOrders(foundOrders);
                
                // Process ALL containers immediately - no delays
                for (int i = 0; i < orderCount; i++) {
                    try {
                        Locator container = orderContainers.nth(i);
                        
                        // Extract order URL for tracking instantly
                        Locator linkElement = container.locator(".orderA-converted__name");
                        if (linkElement.count() > 0) {
                            String href = linkElement.getAttribute("href");
                            if (href != null && !href.isEmpty()) {
                                String fullUrl = BASE_URL + href;
                                
                                // Check if we've already processed this order
                                // REMOVED: Duplicate order prevention - bid on ALL orders every time
                                // if (!processedOrders.contains(fullUrl)) {
                                    // INSTANT processing - no delays between orders
                                    processOrderFromSearchPageInstant(container, fullUrl);
                                    // REMOVED: processedOrders.add(fullUrl); - allow re-bidding
                                    // NO DELAY - process next order immediately
                                // }
                            }
                        }
                    } catch (Exception e) {
                        // Continue to next order if this one fails
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            // Silent DOM collection errors - continue operation
        }
    }
    
    private void processOrderFromSearchPageInstant(Locator orderContainer, String orderUrl) {
        try {
            // ULTRA-FAST: Extract only essential data instantly
            String title = "Unknown Order";
            int bidCount = 0;
            
            try {
                Locator titleElement = orderContainer.locator(".orderA-converted__name");
                if (titleElement.count() > 0) {
                    title = titleElement.textContent().trim();
                }
                
                Locator bidCountElement = orderContainer.locator(".orderA-converted__bidsCounter");
                if (bidCountElement.count() > 0) {
                    String bidText = bidCountElement.textContent().trim();
                    bidCount = extractNumberFromText(bidText);
                }
            } catch (Exception e) {
                // Continue with defaults if extraction fails
            }
            
            // NO FILTERING - bid on ALL orders for maximum capture rate
            // All orders that appear should be processed regardless of competition or title
            
            // Notify that an order was found
            app.notifyOrderFound(title);
            
            // Generate FAST message - use simple template
            String fastMessage = generateFastBidMessage(title);
            
            // CRITICAL: Find bid button IMMEDIATELY with fallback strategies
            Locator bidButton = orderContainer.locator(BID_BUTTON_SELECTOR);
            if (bidButton.count() > 0) {
                // INSTANT click - no delays
                bidButton.first().click();
                
                // IMMEDIATE form submission with timeout protection
                if (submitBidFormUltraFast(fastMessage)) {
                    successfulBids++;
                    app.updateSuccessfulBids(successfulBids);
                    app.notifyBidSuccess(title);
                } else {
                    // FALLBACK: Try global bid buttons if container disappeared
                    tryGlobalBidPlacement(fastMessage, title);
                }
            } else {
                // FALLBACK: Try global bid buttons immediately
                tryGlobalBidPlacement(fastMessage, title);
            }
            
        } catch (Exception e) {
            // Silent error handling - container might have disappeared
        }
    }
    
    private void tryGlobalBidPlacement(String bidMessage, String title) {
        try {
            // FALLBACK: Look for any available bid buttons on the page
            String[] globalBidSelectors = {
                "button:has-text('Place a Bid')",
                "button:has-text('Make a Bid')",
                "button.styled__MakeBidButton",
                "button[data-testid*='MakeBid']",
                "button[class*='bid']"
            };
            
            for (String selector : globalBidSelectors) {
                try {
                    Locator globalBidButtons = page.locator(selector);
                    if (globalBidButtons.count() > 0) {
                        // Try the first available bid button
                        globalBidButtons.first().click();
                        Thread.sleep(50); // Minimal delay
                        
                        if (submitBidFormUltraFast(bidMessage)) {
                            successfulBids++;
                            app.updateSuccessfulBids(successfulBids);
                            app.notifyBidSuccess(title);
                            return; // Success - exit
                        }
                    }
                } catch (Exception e) {
                    // Try next selector
                    continue;
                }
            }
        } catch (Exception e) {
            // Silent fallback failure
        }
    }
    
    private void tryParallelBidPlacement() {
        try {
            // EXPERIMENTAL: Try to place multiple bids simultaneously
            Locator allBidButtons = page.locator(BID_BUTTON_SELECTOR);
            int buttonCount = allBidButtons.count();
            
            if (buttonCount > 0) {
                // Click multiple bid buttons rapidly
                for (int i = 0; i < Math.min(buttonCount, 3); i++) { // Limit to 3 simultaneous attempts
                    try {
                        allBidButtons.nth(i).click();
                        Thread.sleep(10); // Ultra-minimal delay between clicks
                    } catch (Exception e) {
                        continue;
                    }
                }
                
                // Try to fill any open forms
                String fastMessage = "Hi! Ready to deliver excellent results immediately!";
                submitBidFormUltraFast(fastMessage);
            }
        } catch (Exception e) {
            // Silent parallel processing errors
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
        // NO FILTERING - process ALL orders for maximum capture rate
        return true; // Bid on every order that appears, regardless of competition or title
    }
    
    private String getSkipReason(OrderDetails order) {
        // NO FILTERING APPLIED - all orders are processed
        return "no filtering applied";
    }
    
    private String generateFastBidMessage(String title) {
        // ULTRA-FAST message generation - no complex analysis
        String[] fastTemplates = {
            "Hi! I can deliver excellent results for this project. Ready to start immediately!",
            "Hello! I have the expertise needed for this task. Let's discuss your requirements!",
            "Greetings! I'm available to complete this project with high quality. Contact me now!"
        };
        
        return fastTemplates[ThreadLocalRandom.current().nextInt(fastTemplates.length)];
    }
    
    private boolean submitBidFormUltraFast(String bidMessage) {
        try {
            // ULTRA-FAST: Wait minimal time for modal - NEVER TIMEOUT
            Locator bidForm = page.locator(".ui-modal-content, .sb-makeOffer-converted__bid");
            try {
                bidForm.first().waitFor(new Locator.WaitForOptions().setTimeout(500)); // Only 500ms
            } catch (Exception e) {
                // Continue even if modal doesn't appear - maybe it's already there
            }
            
            // Fill text area INSTANTLY
            Locator textArea = page.locator(".auctionTextarea-converted__textarea");
            if (textArea.count() > 0) {
                textArea.first().fill(bidMessage);
                Thread.sleep(50); // Only 50ms delay
                
                // Find submit button with multiple selector strategies - AGGRESSIVE RETRY
                String[] submitSelectors = {
                    "button:has-text('Place a Bid'):not([disabled])",
                    "button:has-text('Submit'):not([disabled])",
                    "button:has-text('Send Bid'):not([disabled])",
                    "button[type='submit']:not([disabled])",
                    "button:has-text('Place a Bid')", // Try even if disabled
                    "button:has-text('Submit')", // Try even if disabled
                    "button[type='submit']" // Try even if disabled
                };
                
                // AGGRESSIVE: Try each selector multiple times
                for (String selector : submitSelectors) {
                    for (int attempt = 0; attempt < 3; attempt++) { // 3 attempts per selector
                        try {
                            Locator submitButton = page.locator(selector);
                            if (submitButton.count() > 0) {
                                submitButton.first().click();
                                Thread.sleep(50); // Minimal verification wait
                                
                                // Quick success check
                                boolean modalGone = page.locator(".ui-modal-content").count() == 0;
                                if (modalGone) {
                                    return true; // SUCCESS!
                                }
                            }
                        } catch (Exception e) {
                            // Continue to next attempt
                            continue;
                        }
                    }
                }
                
                // If no submit button worked, try to close modal
                try {
                    Locator cancelButton = page.locator("button:has-text('Cancel'), .ui-modal-close");
                    if (cancelButton.count() > 0) {
                        cancelButton.first().click();
                    }
                } catch (Exception ignored) {}
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
