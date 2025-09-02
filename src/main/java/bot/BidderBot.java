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
            app.logMessage("🔍 COMPREHENSIVE ORDER DETECTION STARTING...");
            
            // STEP 1: Test multiple container selectors to find ALL orders
            String[] allPossibleSelectors = {
                ".orderA-converted__order",                    // Primary selector
                ".orderA-converted__contentWrapper",           // Alternative 1
                ".order-item",                                // Alternative 2  
                "[class*='order'][class*='container']",       // Generic order containers
                "[class*='order'][class*='wrapper']",         // Generic order wrappers
                ".order",                                     // Simple order class
                "[data-testid*='order']",                    // TestID based
                "[class*='Order']",                          // Capital O Order
                "div[class*='order']",                       // Div with order class
                "article[class*='order']",                   // Article elements
                "section[class*='order']",                   // Section elements
                "[id*='order']",                             // ID based selectors
                "tr[class*='order']",                        // Table row orders
                "li[class*='order']",                        // List item orders
                "[class*='bid'][class*='item']",             // Bid items
                "[class*='auction']",                        // Auction items
                "[class*='project']",                        // Project items
                "[class*='task']",                           // Task items
                "[class*='job']",                            // Job items
                "[role='listitem']",                         // ARIA list items
                "[role='article']",                          // ARIA articles
            };
            
            int totalFoundOrders = 0;
            Locator bestSelector = null;
            String bestSelectorName = "";
            
            // Test each selector and find the one with most results
            for (String selector : allPossibleSelectors) {
                try {
                    Locator containers = page.locator(selector);
                    int count = containers.count();
                    app.logMessage("🔍 Selector '" + selector + "' found " + count + " elements");
                    
                    if (count > totalFoundOrders) {
                        totalFoundOrders = count;
                        bestSelector = containers;
                        bestSelectorName = selector;
                        app.logMessage("⬆️ NEW BEST SELECTOR: '" + selector + "' with " + count + " orders");
                    }
                } catch (Exception e) {
                    app.logMessage("⚠️ Error testing selector '" + selector + "': " + e.getMessage());
                }
            }
            
            app.logMessage("🏆 BEST SELECTOR: '" + bestSelectorName + "' found " + totalFoundOrders + " orders");
            
            // STEP 2: Check for pagination/load more content
            checkForMoreContent();
            
            // STEP 3: Verify we're seeing all available orders
            verifyCompleteOrderVisibility();
            
            // STEP 4: Process orders with the best selector
            if (totalFoundOrders > 0 && bestSelector != null) {
                foundOrders += totalFoundOrders;
                app.updateFoundOrders(foundOrders);
                
                app.logMessage("⚡ PROCESSING " + totalFoundOrders + " ORDERS using best selector...");
                
                // Process ALL containers immediately - no delays
                for (int i = 0; i < totalFoundOrders; i++) {
                    try {
                        Locator container = bestSelector.nth(i);
                        
                        // Extract order URL for tracking instantly
                        String fullUrl = extractOrderUrl(container);
                        
                        if (!fullUrl.isEmpty()) {
                            app.logMessage("🔗 ORDER " + (i + 1) + "/" + totalFoundOrders + ": " + fullUrl);
                            
                            // INSTANT processing - no delays between orders
                            processOrderFromSearchPageInstant(container, fullUrl);
                        } else {
                            app.logMessage("⚠️ ORDER " + (i + 1) + " has no valid URL, analyzing container...");
                            analyzeContainerContent(container, i + 1);
                        }
                    } catch (Exception e) {
                        app.logMessage("⚠️ ERROR processing order " + (i + 1) + ": " + e.getMessage());
                        continue;
                    }
                }
                
                app.logMessage("✅ FINISHED PROCESSING " + totalFoundOrders + " ORDERS");
            } else {
                app.logMessage("❌ NO ORDERS FOUND WITH ANY SELECTOR!");
                app.logMessage("🔍 PERFORMING DEEP PAGE ANALYSIS...");
                performDeepPageAnalysis();
            }
        } catch (Exception e) {
            app.logMessage("💥 ERROR in collectOrdersFromCurrentDOM: " + e.getMessage());
            e.printStackTrace();
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
            app.logMessage("📝 PROCESSING ORDER: " + title + " (" + bidCount + " bids)");
            
            // Generate FAST message - use simple template
            String fastMessage = generateFastBidMessage(title);
            
            // CRITICAL DEBUG: Check for bid buttons with extensive logging
            app.logMessage("🔍 SEARCHING FOR BID BUTTON...");
            
            // Try multiple bid button selector strategies
            String[] bidButtonSelectors = {
                "button.styled__MakeBidButton-sc-18augvm-9",
                "button[data-testid*='MakeBid']", 
                "button:has-text('Place a Bid')",
                "button:has-text('Make a Bid')",
                "button:has-text('Bid')",
                "button[class*='MakeBid']",
                "button[class*='bid']",
                ".styled__MakeBidButton",
                "[data-cy='make-bid-button']",
                "button[type='button']:has-text('Bid')"
            };
            
            boolean bidButtonFound = false;
            String usedSelector = "";
            
            for (String selector : bidButtonSelectors) {
                try {
                    Locator bidButton = orderContainer.locator(selector);
                    int buttonCount = bidButton.count();
                    app.logMessage("🔍 Selector '" + selector + "' found " + buttonCount + " buttons");
                    
                    if (buttonCount > 0) {
                        app.logMessage("✅ FOUND BID BUTTON with selector: " + selector);
                        usedSelector = selector;
                        
                        // INSTANT click - no delays
                        app.logMessage("🖱️ CLICKING BID BUTTON...");
                        bidButton.first().click();
                        Thread.sleep(100); // Small delay to let modal appear
                        
                        app.logMessage("⚡ BID BUTTON CLICKED! Attempting form submission...");
                        
                        // IMMEDIATE form submission with timeout protection
                        if (submitBidFormUltraFast(fastMessage)) {
                            successfulBids++;
                            app.updateSuccessfulBids(successfulBids);
                            app.notifyBidSuccess(title);
                            app.logMessage("🎉 BID SUCCESSFULLY PLACED for: " + title);
                            return; // Success!
                        } else {
                            app.logMessage("❌ BID FORM SUBMISSION FAILED for: " + title);
                            // Continue to try other selectors
                        }
                        bidButtonFound = true;
                        break; // Exit loop after first successful click
                    }
                } catch (Exception e) {
                    app.logMessage("⚠️ Error with selector '" + selector + "': " + e.getMessage());
                    continue;
                }
            }
            
            if (!bidButtonFound) {
                app.logMessage("❌ NO BID BUTTON FOUND! Trying global fallback...");
                // FALLBACK: Try global bid buttons if container disappeared
                if (!tryGlobalBidPlacement(fastMessage, title)) {
                    // ULTIMATE FALLBACK: Try individual page navigation strategy
                    tryIndividualPageBidPlacement(orderUrl, fastMessage, title);
                }
            }
            
        } catch (Exception e) {
            app.logMessage("💥 ERROR in processOrderFromSearchPageInstant: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean tryGlobalBidPlacement(String bidMessage, String title) {
        try {
            app.logMessage("🌍 TRYING GLOBAL BID PLACEMENT for: " + title);
            
            // FALLBACK: Look for any available bid buttons on the page
            String[] globalBidSelectors = {
                "button:has-text('Place a Bid')",
                "button:has-text('Make a Bid')",
                "button.styled__MakeBidButton",
                "button[data-testid*='MakeBid']",
                "button[class*='bid']",
                "button:has-text('Bid')",
                "[data-cy='make-bid-button']"
            };
            
            for (String selector : globalBidSelectors) {
                try {
                    Locator globalBidButtons = page.locator(selector);
                    int buttonCount = globalBidButtons.count();
                    app.logMessage("🌍 Global selector '" + selector + "' found " + buttonCount + " buttons");
                    
                    if (buttonCount > 0) {
                        app.logMessage("✅ FOUND GLOBAL BID BUTTON with selector: " + selector);
                        
                        // Try the first available bid button
                        app.logMessage("🖱️ CLICKING GLOBAL BID BUTTON...");
                        globalBidButtons.first().click();
                        Thread.sleep(100); // Minimal delay
                        
                        app.logMessage("⚡ GLOBAL BID BUTTON CLICKED! Attempting form submission...");
                        
                        if (submitBidFormUltraFast(bidMessage)) {
                            successfulBids++;
                            app.updateSuccessfulBids(successfulBids);
                            app.notifyBidSuccess(title);
                            app.logMessage("🎉 GLOBAL BID SUCCESSFULLY PLACED for: " + title);
                            return true; // Success - exit
                        } else {
                            app.logMessage("❌ GLOBAL BID FORM SUBMISSION FAILED for: " + title);
                        }
                    }
                } catch (Exception e) {
                    app.logMessage("⚠️ Error with global selector '" + selector + "': " + e.getMessage());
                    continue;
                }
            }
            
            app.logMessage("❌ NO GLOBAL BID BUTTONS FOUND for: " + title);
            return false;
            
        } catch (Exception e) {
            app.logMessage("💥 ERROR in tryGlobalBidPlacement: " + e.getMessage());
            return false;
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
    
    private void tryIndividualPageBidPlacement(String orderUrl, String bidMessage, String title) {
        Page orderPage = null;
        try {
            app.logMessage("🎆 ULTIMATE FALLBACK: Individual page navigation for: " + title);
            app.logMessage("🔗 Navigating to: " + orderUrl);
            
            // Create new page for individual order
            orderPage = context.newPage();
            orderPage.setDefaultTimeout(5000); // 5 second timeout for fallback
            orderPage.setDefaultNavigationTimeout(10000); // 10 second navigation timeout
            
            // Navigate to individual order page
            orderPage.navigate(orderUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            app.logMessage("✅ NAVIGATED to individual order page");
            
            // Wait briefly for page to stabilize
            Thread.sleep(500);
            
            // Look for bid button on individual page with multiple selectors
            String[] individualPageBidSelectors = {
                "button:has-text('Place a Bid')",
                "button:has-text('Make a Bid')", 
                "button:has-text('Bid')",
                "button[class*='bid']",
                "button[data-testid*='bid']",
                ".bid-button",
                "[data-cy*='bid']",
                "input[type='submit'][value*='bid']"
            };
            
            boolean bidButtonClicked = false;
            for (String selector : individualPageBidSelectors) {
                try {
                    Locator bidButton = orderPage.locator(selector);
                    int buttonCount = bidButton.count();
                    app.logMessage("🔍 Individual page selector '" + selector + "' found " + buttonCount + " buttons");
                    
                    if (buttonCount > 0) {
                        // Check if button is visible
                        if (bidButton.first().isVisible()) {
                            app.logMessage("✅ FOUND VISIBLE BID BUTTON on individual page: " + selector);
                            app.logMessage("🖱️ CLICKING INDIVIDUAL PAGE BID BUTTON...");
                            
                            bidButton.first().click();
                            Thread.sleep(200); // Allow time for modal/form to appear
                            bidButtonClicked = true;
                            
                            app.logMessage("⚡ INDIVIDUAL PAGE BID BUTTON CLICKED!");
                            break;
                        } else {
                            app.logMessage("⚠️ Bid button found but not visible: " + selector);
                        }
                    }
                } catch (Exception e) {
                    app.logMessage("⚠️ Error with individual page selector '" + selector + "': " + e.getMessage());
                    continue;
                }
            }
            
            if (!bidButtonClicked) {
                app.logMessage("❌ NO BID BUTTON FOUND on individual page for: " + title);
                return;
            }
            
            // Try to fill message and submit on individual page
            String[] messageSelectors = {
                "textarea[name='message']",
                "textarea[placeholder*='message']",
                "textarea[placeholder*='bid']", 
                "textarea[class*='message']",
                "textarea[class*='bid']",
                "textarea",
                "input[type='text'][name*='message']",
                "[contenteditable='true']"
            };
            
            boolean messageEntered = false;
            for (String msgSelector : messageSelectors) {
                try {
                    Locator messageField = orderPage.locator(msgSelector);
                    if (messageField.count() > 0) {
                        app.logMessage("✅ FOUND MESSAGE FIELD on individual page: " + msgSelector);
                        
                        // Wait for field to be visible and fill it
                        messageField.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3000));
                        messageField.first().fill(bidMessage);
                        
                        app.logMessage("✍️ MESSAGE FILLED on individual page!");
                        messageEntered = true;
                        break;
                    }
                } catch (Exception e) {
                    app.logMessage("⚠️ Error filling message with selector '" + msgSelector + "': " + e.getMessage());
                    continue;
                }
            }
            
            if (!messageEntered) {
                app.logMessage("❌ NO MESSAGE FIELD FOUND on individual page for: " + title);
                return;
            }
            
            // Try to submit on individual page
            String[] submitSelectors = {
                "button[type='submit']",
                "button:has-text('Submit')",
                "button:has-text('Place Bid')",
                "button:has-text('Send')",
                "input[type='submit']",
                "button[class*='submit']",
                "[data-cy*='submit']"
            };
            
            boolean submitted = false;
            for (String submitSelector : submitSelectors) {
                try {
                    Locator submitButton = orderPage.locator(submitSelector);
                    if (submitButton.count() > 0) {
                        app.logMessage("✅ FOUND SUBMIT BUTTON on individual page: " + submitSelector);
                        
                        // Wait for submit button to be visible and click it
                        submitButton.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3000));
                        submitButton.first().click();
                        
                        app.logMessage("🖱️ SUBMIT CLICKED on individual page!");
                        
                        // Wait a moment to see if submission was successful
                        Thread.sleep(1000);
                        
                        // Check for success indicators
                        String currentUrl = orderPage.url();
                        if (currentUrl.contains("success") || 
                            orderPage.locator("text=success").count() > 0 ||
                            orderPage.locator("text=submitted").count() > 0 ||
                            orderPage.locator("text=thank you").count() > 0) {
                            
                            submitted = true;
                            successfulBids++;
                            app.updateSuccessfulBids(successfulBids);
                            app.notifyBidSuccess(title);
                            app.logMessage("🎉 INDIVIDUAL PAGE BID SUCCESSFULLY PLACED for: " + title);
                            break;
                        } else {
                            app.logMessage("⚠️ Submit clicked but no success confirmation for: " + title);
                        }
                    }
                } catch (Exception e) {
                    app.logMessage("⚠️ Error with submit selector '" + submitSelector + "': " + e.getMessage());
                    continue;
                }
            }
            
            if (!submitted) {
                app.logMessage("❌ INDIVIDUAL PAGE BID SUBMISSION FAILED for: " + title);
            }
            
        } catch (Exception e) {
            app.logMessage("💥 ERROR in tryIndividualPageBidPlacement: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always close the individual page
            if (orderPage != null) {
                try {
                    orderPage.close();
                    app.logMessage("🚪 CLOSED individual order page");
                } catch (Exception ignored) {}
            }
        }
    }
    
    private void checkForMoreContent() {
        try {
            app.logMessage("🔍 CHECKING FOR PAGINATION/LOAD MORE CONTENT...");
            
            // Check for pagination buttons
            String[] paginationSelectors = {
                "button:has-text('Load More')",
                "button:has-text('Show More')",
                "button:has-text('Next')",
                ".pagination button",
                "[class*='pagination']",
                "[class*='load-more']",
                "[class*='show-more']",
                "button[class*='next']",
                "a[class*='next']"
            };
            
            for (String selector : paginationSelectors) {
                int count = page.locator(selector).count();
                if (count > 0) {
                    app.logMessage("✅ FOUND PAGINATION: '" + selector + "' (" + count + " elements)");
                    // Try clicking to load more content
                    try {
                        page.locator(selector).first().click();
                        app.logMessage("🖱️ CLICKED PAGINATION BUTTON");
                        Thread.sleep(1000); // Wait for content to load
                        break;
                    } catch (Exception e) {
                        app.logMessage("⚠️ Failed to click pagination: " + e.getMessage());
                    }
                }
            }
            
            // Try infinite scroll
            app.logMessage("🔍 TRYING INFINITE SCROLL...");
            for (int i = 0; i < 3; i++) {
                page.evaluate("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(500);
                page.evaluate("window.scrollTo(0, 0);");
                Thread.sleep(500);
            }
            
        } catch (Exception e) {
            app.logMessage("⚠️ Error checking for more content: " + e.getMessage());
        }
    }
    
    private String extractOrderUrl(Locator container) {
        String[] linkSelectors = {
            ".orderA-converted__name",
            "a[href*='order']",
            "a[href*='/order/']",
            "a",
            "[href]",
            "[data-href]"
        };
        
        for (String linkSelector : linkSelectors) {
            try {
                Locator linkElement = container.locator(linkSelector);
                if (linkElement.count() > 0) {
                    String href = linkElement.first().getAttribute("href");
                    if (href != null && !href.isEmpty()) {
                        return href.startsWith("http") ? href : BASE_URL + href;
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }
        
        return "";
    }
    
    private void analyzeContainerContent(Locator container, int containerNumber) {
        try {
            app.logMessage("🔍 ANALYZING CONTAINER " + containerNumber + " CONTENT...");
            
            // Get all text content
            String containerText = container.textContent();
            app.logMessage("📝 Container " + containerNumber + " text: " + 
                          (containerText.length() > 100 ? containerText.substring(0, 100) + "..." : containerText));
            
            // Check for typical order indicators
            String[] orderIndicators = {"deadline", "budget", "bid", "proposal", "due", "$", "€", "£", "pages", "words"};
            for (String indicator : orderIndicators) {
                if (containerText.toLowerCase().contains(indicator.toLowerCase())) {
                    app.logMessage("✅ Container " + containerNumber + " contains order indicator: '" + indicator + "'");
                }
            }
            
            // Try to find clickable elements
            int clickableCount = container.locator("a, button, [onclick], [href]").count();
            app.logMessage("🖱️ Container " + containerNumber + " has " + clickableCount + " clickable elements");
            
        } catch (Exception e) {
            app.logMessage("⚠️ Error analyzing container " + containerNumber + ": " + e.getMessage());
        }
    }
    
    private void verifyCompleteOrderVisibility() {
        try {
            app.logMessage("🔍 VERIFYING COMPLETE ORDER VISIBILITY...");
            
            // Check current URL and filters
            String currentUrl = page.url();
            app.logMessage("🔗 CURRENT URL: " + currentUrl);
            
            // Check for active filters that might be limiting results
            String[] filterSelectors = {
                "[class*='filter'][class*='active']",
                "[class*='selected']",
                "input[type='checkbox']:checked",
                "select option:checked",
                ".active",
                "[aria-selected='true']"
            };
            
            for (String filterSelector : filterSelectors) {
                try {
                    int filterCount = page.locator(filterSelector).count();
                    if (filterCount > 0) {
                        app.logMessage("🔍 ACTIVE FILTERS DETECTED: '" + filterSelector + "' (" + filterCount + " active)");
                        // Log what filters are active
                        for (int i = 0; i < Math.min(filterCount, 5); i++) {
                            String filterText = page.locator(filterSelector).nth(i).textContent();
                            app.logMessage("  • Filter " + (i + 1) + ": " + filterText);
                        }
                    }
                } catch (Exception e) {
                    // Continue checking other filters
                }
            }
            
            // Check if we need to clear filters to see all orders
            tryToClearFilters();
            
        } catch (Exception e) {
            app.logMessage("⚠️ Error verifying order visibility: " + e.getMessage());
        }
    }
    
    private void tryToClearFilters() {
        try {
            app.logMessage("🔍 ATTEMPTING TO CLEAR FILTERS FOR MAXIMUM VISIBILITY...");
            
            // Try to find and click "Clear All" or "Reset" buttons
            String[] clearFilterSelectors = {
                "button:has-text('Clear All')",
                "button:has-text('Reset')",
                "button:has-text('Clear Filters')",
                "[class*='clear'][class*='filter']",
                "[class*='reset']",
                "button[type='reset']"
            };
            
            for (String selector : clearFilterSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        app.logMessage("✅ FOUND CLEAR FILTER BUTTON: " + selector);
                        page.locator(selector).first().click();
                        app.logMessage("🖱️ CLICKED CLEAR FILTERS");
                        Thread.sleep(1000); // Wait for filters to clear
                        return;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            
            // Try to navigate to "all orders" view
            String[] allOrdersSelectors = {
                "a:has-text('All Orders')",
                "button:has-text('All')",
                "[href*='all']",
                "[class*='all'][class*='order']"
            };
            
            for (String selector : allOrdersSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        app.logMessage("✅ FOUND ALL ORDERS LINK: " + selector);
                        page.locator(selector).first().click();
                        app.logMessage("🖱️ CLICKED ALL ORDERS");
                        Thread.sleep(2000); // Wait for page to load
                        return;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
            
        } catch (Exception e) {
            app.logMessage("⚠️ Error clearing filters: " + e.getMessage());
        }
    }
    
    private void performDeepPageAnalysis() {
        try {
            app.logMessage("🔬 PERFORMING DEEP PAGE ANALYSIS...");
            
            // Get page title and URL
            String pageTitle = page.title();
            String pageUrl = page.url();
            app.logMessage("📋 PAGE TITLE: " + pageTitle);
            app.logMessage("🔗 PAGE URL: " + pageUrl);
            
            // Check if we're actually on the orders page
            if (!pageUrl.contains("order") && !pageUrl.contains("search")) {
                app.logMessage("⚠️ WARNING: May not be on orders page!");
                app.logMessage("🔍 ATTEMPTING TO NAVIGATE TO ORDERS PAGE...");
                page.navigate(ORDERS_URL);
                Thread.sleep(3000);
                return;
            }
            
            // Count all elements on page
            int totalElements = page.locator("*").count();
            app.logMessage("📋 TOTAL PAGE ELEMENTS: " + totalElements);
            
            // Look for any text that might indicate orders
            String pageContent = page.content();
            String[] orderKeywords = {"order", "bid", "project", "task", "assignment", "deadline", "budget"};
            
            for (String keyword : orderKeywords) {
                int occurrences = pageContent.toLowerCase().split(keyword.toLowerCase()).length - 1;
                if (occurrences > 0) {
                    app.logMessage("🔍 Keyword '" + keyword + "' appears " + occurrences + " times on page");
                }
            }
            
            // Try to find any potential order containers with very broad selectors
            String[] broadSelectors = {"div", "article", "section", "li", "tr"};
            for (String selector : broadSelectors) {
                int count = page.locator(selector).count();
                app.logMessage("📋 Found " + count + " '" + selector + "' elements");
            }
            
        } catch (Exception e) {
            app.logMessage("⚠️ Error in deep page analysis: " + e.getMessage());
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
            app.logMessage("📄 STARTING BID FORM SUBMISSION...");
            
            // ULTRA-FAST: Wait minimal time for modal - NEVER TIMEOUT
            Locator bidForm = page.locator(".ui-modal-content, .sb-makeOffer-converted__bid");
            try {
                app.logMessage("⌛ WAITING FOR MODAL TO APPEAR...");
                bidForm.first().waitFor(new Locator.WaitForOptions().setTimeout(500)); // Only 500ms
                app.logMessage("✅ MODAL APPEARED!");
            } catch (Exception e) {
                app.logMessage("⚠️ MODAL TIMEOUT - continuing anyway (modal might already be there)");
                // Continue even if modal doesn't appear - maybe it's already there
            }
            
            // Check if ANY form of modal/form is present
            String[] modalSelectors = {
                ".ui-modal-content",
                ".sb-makeOffer-converted__bid", 
                ".modal",
                "[role='dialog']",
                ".popup",
                ".overlay",
                "form[class*='bid']",
                "div[class*='bid'][class*='form']"
            };
            
            boolean modalFound = false;
            for (String modalSelector : modalSelectors) {
                int modalCount = page.locator(modalSelector).count();
                app.logMessage("🔍 Modal selector '" + modalSelector + "' found " + modalCount + " elements");
                if (modalCount > 0) {
                    modalFound = true;
                    break;
                }
            }
            
            if (!modalFound) {
                app.logMessage("❌ NO MODAL/FORM FOUND! Trying to proceed anyway...");
            }
            
            // Fill text area INSTANTLY with multiple selector strategies
            String[] textAreaSelectors = {
                ".auctionTextarea-converted__textarea",
                "textarea[name='message']",
                "textarea[placeholder*='bid']",
                "textarea[class*='bid']",
                "textarea[class*='message']",
                "textarea",
                "input[type='text'][class*='bid']",
                "[contenteditable='true']"
            };
            
            boolean textAreaFilled = false;
            for (String textAreaSelector : textAreaSelectors) {
                try {
                    Locator textArea = page.locator(textAreaSelector);
                    int textAreaCount = textArea.count();
                    app.logMessage("🔍 TextArea selector '" + textAreaSelector + "' found " + textAreaCount + " elements");
                    
                    if (textAreaCount > 0) {
                        app.logMessage("✅ FOUND TEXT AREA with selector: " + textAreaSelector);
                        app.logMessage("✍️ FILLING TEXT AREA with message: " + bidMessage.substring(0, Math.min(50, bidMessage.length())) + "...");
                        
                        textArea.first().fill(bidMessage);
                        Thread.sleep(50); // Only 50ms delay
                        
                        app.logMessage("✅ TEXT AREA FILLED SUCCESSFULLY!");
                        textAreaFilled = true;
                        break;
                    }
                } catch (Exception e) {
                    app.logMessage("⚠️ Error filling text area with selector '" + textAreaSelector + "': " + e.getMessage());
                    continue;
                }
            }
            
            if (!textAreaFilled) {
                app.logMessage("❌ NO TEXT AREA FOUND! Cannot fill bid message.");
                return false;
            }
            
            // Find submit button with multiple selector strategies - AGGRESSIVE RETRY
            String[] submitSelectors = {
                "button:has-text('Place a Bid'):not([disabled])",
                "button:has-text('Submit'):not([disabled])",
                "button:has-text('Send Bid'):not([disabled])",
                "button[type='submit']:not([disabled])",
                "button:has-text('Place a Bid')", // Try even if disabled
                "button:has-text('Submit')", // Try even if disabled
                "button[type='submit']", // Try even if disabled
                "button:has-text('Send')",
                "button[class*='submit']",
                "input[type='submit']",
                "button[value*='submit']"
            };
            
            app.logMessage("🔍 SEARCHING FOR SUBMIT BUTTON...");
            
            // AGGRESSIVE: Try each selector multiple times
            for (String selector : submitSelectors) {
                for (int attempt = 0; attempt < 3; attempt++) { // 3 attempts per selector
                    try {
                        Locator submitButton = page.locator(selector);
                        int submitCount = submitButton.count();
                        app.logMessage("🔍 Submit selector '" + selector + "' (attempt " + (attempt + 1) + ") found " + submitCount + " buttons");
                        
                        if (submitCount > 0) {
                            app.logMessage("✅ FOUND SUBMIT BUTTON with selector: " + selector);
                            app.logMessage("🖱️ CLICKING SUBMIT BUTTON...");
                            
                            submitButton.first().click();
                            Thread.sleep(50); // Minimal verification wait
                            
                            app.logMessage("⚡ SUBMIT BUTTON CLICKED! Checking for success...");
                            
                            // Quick success check
                            boolean modalGone = page.locator(".ui-modal-content").count() == 0;
                            if (modalGone) {
                                app.logMessage("🎉 MODAL DISAPPEARED - BID SUBMISSION SUCCESS!");
                                return true; // SUCCESS!
                            } else {
                                app.logMessage("⚠️ Modal still present, checking other indicators...");
                                // Additional success checks
                                Thread.sleep(100);
                                if (page.locator(".ui-modal-content").count() == 0 || 
                                    page.locator("text=success").count() > 0 ||
                                    page.locator("text=submitted").count() > 0) {
                                    app.logMessage("🎉 BID SUBMISSION SUCCESS (secondary check)!");
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        app.logMessage("⚠️ Error with submit selector '" + selector + "' (attempt " + (attempt + 1) + "): " + e.getMessage());
                        continue;
                    }
                }
            }
            
            app.logMessage("❌ NO SUBMIT BUTTON WORKED! Attempting modal closure...");
            
            // If no submit button worked, try to close modal
            try {
                Locator cancelButton = page.locator("button:has-text('Cancel'), .ui-modal-close");
                if (cancelButton.count() > 0) {
                    app.logMessage("🚪 CLOSING MODAL with cancel button...");
                    cancelButton.first().click();
                }
            } catch (Exception ignored) {}
            
            app.logMessage("❌ BID FORM SUBMISSION FAILED!");
            return false;
            
        } catch (Exception e) {
            app.logMessage("💥 EXCEPTION in submitBidFormUltraFast: " + e.getMessage());
            e.printStackTrace();
            
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
