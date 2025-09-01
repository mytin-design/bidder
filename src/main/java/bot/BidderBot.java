package bot;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BidderBot {
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
    private static final String BID_BUTTON_SELECTOR = "#showBidForm";
    private static final String BID_TEXT_SELECTOR = ".auctionTextarea-converted__textarea";
    private static final String SUBMIT_BID_SELECTOR = "button.styled__StyledButton-sc-6klmhm-0";
    
    public BidderBot(String username, String password, String bidText, BidderApp app) {
        this.username = username;
        this.password = password;
        this.bidText = bidText;
        this.app = app;
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
        app.logMessage("Starting order monitoring...");
        
        while (running) {
            try {
                // Show polling message with animated dots
                showPollingMessage();
                
                // Navigate to orders page
                page.navigate(ORDERS_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                Thread.sleep(2000);
                
                // Find order links using the correct selector
                Locator orderLinks = page.locator(ORDER_LINK_SELECTOR);
                int orderCount = orderLinks.count();
                
                if (orderCount > 0) {
                    foundOrders += orderCount;
                    app.updateFoundOrders(foundOrders);
                    
                    // Process each order
                    for (int i = 0; i < orderCount && running; i++) {
                        try {
                            String href = orderLinks.nth(i).getAttribute("href");
                            if (href != null && !href.isEmpty()) {
                                String fullUrl = BASE_URL + href; // Always prepend base URL
                                
                                if (!processedOrders.contains(fullUrl)) {
                                    app.logMessage("Order found!");
                                    processOrder(fullUrl);
                                    processedOrders.add(fullUrl);
                                }
                            }
                        } catch (Exception e) {
                            // Silent error handling - don't spam logs
                        }
                    }
                }
                
                // Reload page and wait before next scan
                page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                Thread.sleep(5000);
                
            } catch (Exception e) {
                // Silent error handling during polling
                Thread.sleep(5000);
            }
        }
    }
    
    private void showPollingMessage() {
        pollingDots = (pollingDots + 1) % 4; // Cycle through 0, 1, 2, 3
        String dots = ".".repeat(pollingDots);
        String message = "Polling" + dots + " ".repeat(3 - pollingDots); // Pad to keep consistent length
        app.logMessage(message);
    }
    
    private void processOrder(String orderUrl) {
        try {
            // Open order in new page
            Page orderPage = context.newPage();
            orderPage.setDefaultTimeout(15000);
            
            try {
                orderPage.navigate(orderUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                Thread.sleep(2000);
                
                // Try to find and click bid button
                try {
                    orderPage.waitForSelector(BID_BUTTON_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(5000));
                    orderPage.click(BID_BUTTON_SELECTOR);
                    Thread.sleep(1000);
                    
                    // Fill bid text
                    orderPage.waitForSelector(BID_TEXT_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(5000));
                    orderPage.type(BID_TEXT_SELECTOR, bidText);
                    
                    // Submit bid
                    orderPage.click(SUBMIT_BID_SELECTOR);
                    Thread.sleep(2000);
                    
                    successfulBids++;
                    app.updateSuccessfulBids(successfulBids);
                    app.logMessage("Bid sent successfully!");
                    
                } catch (Exception bidError) {
                    // Silent error handling - don't spam logs with bid failures
                }
                
            } finally {
                orderPage.close();
            }
            
        } catch (Exception e) {
            // Silent error handling
        }
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
