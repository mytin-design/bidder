package bot;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class BidderApp extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField bidTextField;
    private JLabel foundOrdersLabel;
    private JLabel successfulBidsLabel;
    private JLabel statusLabel;
    private JButton startButton;
    private JButton stopButton;
    private JTextArea logArea;
    private JCheckBox bidPlacementCheckBox;
    
    private BidderBot bot;
    private boolean isRunning = false;
    private javax.swing.Timer animationTimer;
    private int animationFrame = 0;
    private final String[] fishingAnimation = {
        "🎣 Fishing for orders.", 
        "🎣 Fishing for orders..", 
        "🎣 Fishing for orders...",
        "🎣 Fishing for orders...."
    };
    private final String[] pollingAnimation = {
        "🔍 Polling.", 
        "🔍 Polling..", 
        "🔍 Polling...",
        "🔍 Polling...."
    };
    
    public BidderApp() {
        setupLookAndFeel();
        initializeGUI();
        
        // Test: Start a simple animation demo on startup
        javax.swing.Timer testTimer = new javax.swing.Timer(3000, e -> {
            if (!isRunning) {
                // Show fishing animation demo
                updateStatus("🎣 Demo: Fishing for orders...", false);
                
                // Start test animation after 2 seconds
                javax.swing.Timer demoAnimation = new javax.swing.Timer(2000, evt -> {
                    startTestAnimation();
                });
                demoAnimation.setRepeats(false);
                demoAnimation.start();
            }
        });
        testTimer.setRepeats(false);
        testTimer.start();
    }
    
    private void setupLookAndFeel() {
        try {
            // Set system look and feel for better integration
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            // Custom UI properties for modern appearance
            UIManager.put("Button.arc", 15);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextField.arc", 8);
            UIManager.put("PasswordField.arc", 8);
            
        } catch (Exception e) {
            // Fallback to default look and feel
        }
    }
    
    private void initializeGUI() {
        setTitle("🔍 Order Detection Bot - DETECTION ONLY MODE");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 600);
        setLocationRelativeTo(null);
        setResizable(true);
        
        // Modern window styling
        try {
            // Set window icon if available
            setIconImage(createAppIcon());
        } catch (Exception e) {
            // Continue without icon
        }
        
        // Main panel with modern gradient background
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(240, 248, 255),
                    0, getHeight(), new Color(220, 235, 250)
                );
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Top panel - Login credentials
        JPanel loginPanel = createLoginPanel();
        
        // Status panel - Animation and current activity
        JPanel statusPanel = createStatusPanel();
        
        // Middle panel - Statistics
        JPanel statsPanel = createStatsPanel();
        
        // Bottom panel - Controls
        JPanel controlPanel = createControlPanel();
        
        // Create a container for stats and controls
        JPanel bottomContainer = new JPanel(new BorderLayout(10, 10));
        bottomContainer.setOpaque(false);
        bottomContainer.add(statsPanel, BorderLayout.NORTH);
        bottomContainer.add(controlPanel, BorderLayout.CENTER);
        
        mainPanel.add(loginPanel, BorderLayout.NORTH);
        mainPanel.add(statusPanel, BorderLayout.CENTER);
        mainPanel.add(bottomContainer, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private Image createAppIcon() {
        // Create a simple app icon
        BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw bot icon
        g2d.setColor(new Color(52, 152, 219));
        g2d.fillRoundRect(4, 4, 24, 24, 8, 8);
        g2d.setColor(Color.WHITE);
        g2d.fillOval(8, 10, 4, 4);
        g2d.fillOval(20, 10, 4, 4);
        g2d.fillRoundRect(10, 18, 12, 2, 2, 2);
        
        g2d.dispose();
        return icon;
    }
    
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Subtle gradient for login panel
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 200),
                    0, getHeight(), new Color(248, 249, 250, 200)
                );
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
            }
        };
        
        // Modern titled border with custom styling
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219), 2, true),
            "🔑 Login Credentials",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 14),
            new Color(52, 152, 219)
        );
        panel.setBorder(BorderFactory.createCompoundBorder(
            titledBorder,
            new EmptyBorder(10, 15, 15, 15)
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        
        // Username
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        JLabel usernameLabel = new JLabel("👤 Username:");
        usernameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        usernameLabel.setForeground(new Color(52, 73, 94));
        panel.add(usernameLabel, gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        usernameField = createStyledTextField(20);
        usernameField.setToolTipText("Enter your StudyBay username");
        panel.add(usernameField, gbc);
        
        // Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel passwordLabel = new JLabel("🔒 Password:");
        passwordLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        passwordLabel.setForeground(new Color(52, 73, 94));
        panel.add(passwordLabel, gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        passwordField = createStyledPasswordField(20);
        passwordField.setToolTipText("Enter your StudyBay password");
        panel.add(passwordField, gbc);
        
        // Bid Text
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel bidLabel = new JLabel("🤖 AI Mode:");
        bidLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        bidLabel.setForeground(new Color(52, 73, 94));
        panel.add(bidLabel, gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        bidTextField = createStyledTextField(
            "🔍 DETECTION ONLY: Order scanning without bidding functionality", 20
        );
        bidTextField.setEditable(false);
        bidTextField.setBackground(new Color(52, 152, 219, 30));
        bidTextField.setForeground(new Color(41, 128, 185));
        bidTextField.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        bidTextField.setToolTipText("AI will generate personalized bid messages automatically");
        panel.add(bidTextField, gbc);
        
        // Bid Placement Control
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel bidPlacementLabel = new JLabel("🎯 Bid Placement:");
        bidPlacementLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        bidPlacementLabel.setForeground(new Color(52, 73, 94));
        panel.add(bidPlacementLabel, gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        bidPlacementCheckBox = new JCheckBox("✅ Enable Bid Placement (Detection + Bidding)");
        bidPlacementCheckBox.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        bidPlacementCheckBox.setForeground(new Color(41, 128, 185));
        bidPlacementCheckBox.setOpaque(false);
        bidPlacementCheckBox.setSelected(false); // Default to detection only
        bidPlacementCheckBox.setToolTipText("Toggle between detection-only and full bidding functionality");
        bidPlacementCheckBox.addActionListener(e -> {
            boolean enabled = bidPlacementCheckBox.isSelected();
            updateBidPlacementMode(enabled);
            if (bot != null) {
                bot.setBidPlacementEnabled(enabled);
            }
        });
        panel.add(bidPlacementCheckBox, gbc);
        
        return panel;
    }
    
    private void updateBidPlacementMode(boolean enabled) {
        if (enabled) {
            setTitle("🎯 StudyBay Bidding Bot - DETECTION + BIDDING MODE");
            bidTextField.setText("🎯 BIDDING ENABLED: Order detection with automatic bid placement");
            bidTextField.setBackground(new Color(46, 204, 113, 30));
            bidTextField.setForeground(new Color(39, 174, 96));
            startButton.setText("🚀 Start Bidding Bot");
        } else {
            setTitle("🔍 Order Detection Bot - DETECTION ONLY MODE");
            bidTextField.setText("🔍 DETECTION ONLY: Order scanning without bidding functionality");
            bidTextField.setBackground(new Color(52, 152, 219, 30));
            bidTextField.setForeground(new Color(41, 128, 185));
            startButton.setText("🔍 Start Detection Bot");
        }
    }
    
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Animated gradient background
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(52, 152, 219, 30),
                    getWidth(), getHeight(), new Color(155, 89, 182, 30)
                );
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            }
        };
        
        panel.setPreferredSize(new Dimension(0, 120));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        
        // Status label with animation
        statusLabel = new JLabel("🛠️ Bot Ready - Click Start to begin", SwingConstants.CENTER);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        statusLabel.setForeground(new Color(52, 73, 94));
        
        panel.add(statusLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JTextField createStyledTextField(int columns) {
        return createStyledTextField("", columns);
    }
    
    private JTextField createStyledTextField(String text, int columns) {
        JTextField field = new JTextField(text, columns);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(189, 195, 199), 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        field.setBackground(Color.WHITE);
        field.setForeground(new Color(52, 73, 94));
        return field;
    }
    
    private JPasswordField createStyledPasswordField(int columns) {
        JPasswordField field = new JPasswordField(columns);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(189, 195, 199), 1, true),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        field.setBackground(Color.WHITE);
        field.setForeground(new Color(52, 73, 94));
        return field;
    }
    
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Subtle gradient for stats panel
                GradientPaint gradient = new GradientPaint(
                    0, 0, new Color(255, 255, 255, 180),
                    0, getHeight(), new Color(236, 240, 241, 180)
                );
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
            }
        };
        
        // Modern titled border
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(155, 89, 182), 2, true),
            "📈 Performance Statistics",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 14),
            new Color(155, 89, 182)
        );
        panel.setBorder(BorderFactory.createCompoundBorder(
            titledBorder,
            new EmptyBorder(15, 20, 20, 20)
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 15, 10, 15);
        
        // Found Orders Section
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.CENTER;
        JPanel foundOrdersPanel = createStatCard("🔍 Orders Found", "0", new Color(52, 152, 219));
        panel.add(foundOrdersPanel, gbc);
        
        // Successful Bids Section (will show bid placement status)
        gbc.gridx = 1; gbc.gridy = 0;
        JPanel successfulBidsPanel = createStatCard("✅ Successful Bids", "0", new Color(46, 204, 113));
        panel.add(successfulBidsPanel, gbc);
        
        // Success Rate Section
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JPanel successRatePanel = createStatCard("📈 Success Rate", "0%", new Color(230, 126, 34));
        panel.add(successRatePanel, gbc);
        
        return panel;
    }
    
    private JPanel createStatCard(String title, String value, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Card background with shadow effect
                g2d.setColor(new Color(0, 0, 0, 20));
                g2d.fillRoundRect(2, 2, getWidth()-2, getHeight()-2, 10, 10);
                
                g2d.setColor(Color.WHITE);
                g2d.fillRoundRect(0, 0, getWidth()-2, getHeight()-2, 10, 10);
                
                // Accent line
                g2d.setColor(accentColor);
                g2d.fillRoundRect(0, 0, getWidth()-2, 4, 10, 10);
            }
        };
        card.setPreferredSize(new Dimension(150, 80));
        card.setBorder(new EmptyBorder(15, 15, 10, 15));
        
        // Title label
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        titleLabel.setForeground(new Color(127, 140, 141));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Value label (store reference for updates)
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        valueLabel.setForeground(accentColor);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Store references for later updates
        if (title.contains("Orders Found")) {
            foundOrdersLabel = valueLabel;
        } else if (title.contains("Successful Bids")) {
            successfulBidsLabel = valueLabel;
        }
        
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        panel.setOpaque(false);
        
        // Modern Start Button
        startButton = createModernButton("🔍 Start Detection Bot", new Color(46, 204, 113), Color.WHITE);
        startButton.setPreferredSize(new Dimension(200, 45));
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startBot();
            }
        });
        
        // Modern Stop Button
        stopButton = createModernButton("⏹️ Stop Bot", new Color(231, 76, 60), Color.WHITE);
        stopButton.setPreferredSize(new Dimension(150, 45));
        stopButton.setEnabled(false);
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopBot();
            }
        });
        
        panel.add(startButton);
        panel.add(stopButton);
        
        return panel;
    }
    
    private JButton createModernButton(String text, Color backgroundColor, Color textColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Button background with gradient
                if (isEnabled()) {
                    if (getModel().isPressed()) {
                        g2d.setColor(backgroundColor.darker());
                    } else if (getModel().isRollover()) {
                        GradientPaint gradient = new GradientPaint(
                            0, 0, backgroundColor.brighter(),
                            0, getHeight(), backgroundColor
                        );
                        g2d.setPaint(gradient);
                    } else {
                        GradientPaint gradient = new GradientPaint(
                            0, 0, backgroundColor,
                            0, getHeight(), backgroundColor.darker()
                        );
                        g2d.setPaint(gradient);
                    }
                } else {
                    g2d.setColor(new Color(149, 165, 166));
                }
                
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                
                // Text rendering
                g2d.setColor(isEnabled() ? textColor : Color.LIGHT_GRAY);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(getText(), x, y);
            }
        };
        
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setForeground(textColor);
        button.setBackground(backgroundColor);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        return button;
    }
    
    private void startBot() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        // Determine mode based on checkbox
        String bidText = bidPlacementCheckBox.isSelected() ? "BIDDING_ENABLED" : "DETECTION_ONLY";
        
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please fill in username and password before starting the bot.", 
                "Missing Information", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        isRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        
        // Update status based on mode
        String statusMessage = bidPlacementCheckBox.isSelected() ? 
            "🎯 Bidding Bot Starting..." : "🔍 Detection Bot Starting...";
        updateStatus(statusMessage, false);
        
        // Start fishing animation after a brief delay
        javax.swing.Timer startAnimationTimer = new javax.swing.Timer(2000, e -> {
            if (isRunning) {
                startAnimation();
            }
        });
        startAnimationTimer.setRepeats(false);
        startAnimationTimer.start();
        
        bot = new BidderBot(username, password, bidText, this);
        
        // Start bot in separate thread
        Thread botThread = new Thread(() -> {
            try {
                bot.start();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("❌ Error: " + e.getMessage(), false);
                    stopBot();
                });
            }
        });
        botThread.setDaemon(true);
        botThread.start();
    }
    
    private void stopBot() {
        isRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        
        // Stop animation
        stopAnimation();
        
        if (bot != null) {
            bot.stop();
            bot = null;
        }
        
        updateStatus("🛠️ Bot Ready - Click Start to begin", false);
    }
    
    // Animation methods
    private void startAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        
        // Reset animation frame
        animationFrame = 0;
        
        animationTimer = new javax.swing.Timer(800, e -> {
            animationFrame = (animationFrame + 1) % fishingAnimation.length;
            if (isRunning) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(fishingAnimation[animationFrame]);
                    statusLabel.repaint(); // Force repaint
                });
            }
        });
        animationTimer.start();
        System.out.println("Animation timer started with " + fishingAnimation.length + " frames");
    }
    
    private void startTestAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        
        animationFrame = 0;
        
        animationTimer = new javax.swing.Timer(800, e -> {
            animationFrame = (animationFrame + 1) % fishingAnimation.length;
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(fishingAnimation[animationFrame]);
                statusLabel.repaint();
                System.out.println("Animation frame: " + fishingAnimation[animationFrame]); // Debug
            });
        });
        
        // Set first frame immediately
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(fishingAnimation[0]);
            statusLabel.repaint();
            System.out.println("Starting test animation with: " + fishingAnimation[0]);
        });
        
        animationTimer.start();
        System.out.println("Test animation started!");
    }
    
    private void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        animationFrame = 0;
    }
    
    // Sound utility method
    private void playBeep() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception e) {
            // Silent fail if sound not available
        }
    }
    
    // Status update method
    public void updateStatus(String message, boolean withSound) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            if (withSound) {
                playBeep();
            }
        });
    }
    
    // Method to show order found notification
    public void notifyOrderFound(String orderTitle) {
        SwingUtilities.invokeLater(() -> {
            updateStatus("🎯 Order Found: " + (orderTitle.length() > 50 ? orderTitle.substring(0, 50) + "..." : orderTitle), true);
            
            // Return to fishing animation after 3 seconds
            javax.swing.Timer returnTimer = new javax.swing.Timer(3000, e -> {
                if (isRunning && animationTimer != null) {
                    statusLabel.setText(fishingAnimation[animationFrame]);
                }
            });
            returnTimer.setRepeats(false);
            returnTimer.start();
        });
    }
    
    // Method to show bid success notification
    public void notifyBidSuccess(String orderTitle) {
        SwingUtilities.invokeLater(() -> {
            updateStatus("✅ Bid Placed: " + (orderTitle.length() > 50 ? orderTitle.substring(0, 50) + "..." : orderTitle), true);
            
            // Return to fishing animation after 3 seconds
            javax.swing.Timer returnTimer = new javax.swing.Timer(3000, e -> {
                if (isRunning && animationTimer != null) {
                    statusLabel.setText(fishingAnimation[animationFrame]);
                }
            });
            returnTimer.setRepeats(false);
            returnTimer.start();
        });
    }
    
    public void logMessage(String message) {
        // Show ALL debugging messages in the GUI for order detection monitoring
        SwingUtilities.invokeLater(() -> {
            // Format timestamp
            String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            String formattedMessage = "[" + timestamp + "] " + message;
            
            // Update the dedicated log area (activity log)
            if (logArea != null) {
                logArea.append(formattedMessage + "\n");
                // Auto-scroll to bottom
                logArea.setCaretPosition(logArea.getDocument().getLength());
                
                // Limit log area to last 100 lines to prevent memory issues
                String[] lines = logArea.getText().split("\n");
                if (lines.length > 100) {
                    StringBuilder limitedLog = new StringBuilder();
                    for (int i = lines.length - 100; i < lines.length; i++) {
                        limitedLog.append(lines[i]).append("\n");
                    }
                    logArea.setText(limitedLog.toString());
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
            }
            
            // Also update status with the most recent message (shortened)
            String shortMessage = message.length() > 80 ? message.substring(0, 80) + "..." : message;
            updateStatus(shortMessage, false);
            
            // Special handling for critical messages
            if (message.contains("started") || message.contains("ACTIVATED") || message.contains("Initializing")) {
                // Start the fishing animation immediately when bot starts
                // Give a short delay then start the fishing animation
                javax.swing.Timer delayTimer = new javax.swing.Timer(1500, e -> {
                    if (isRunning) {
                        startAnimation();
                    }
                });
                delayTimer.setRepeats(false);
                delayTimer.start();
            }
            
            // Also print to console for terminal visibility
            System.out.println(formattedMessage);
        });
    }
    
    public void updateFoundOrders(int count) {
        SwingUtilities.invokeLater(() -> foundOrdersLabel.setText(String.valueOf(count)));
    }
    
    public void updateSuccessfulBids(int count) {
        SwingUtilities.invokeLater(() -> successfulBidsLabel.setText(String.valueOf(count)));
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BidderApp app = new BidderApp();
            app.setVisible(true);
            
            // Add a test feature: double-click status panel to test animation
            app.statusLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        // Test animation on double-click
                        if (!app.isRunning) {
                            app.isRunning = true;
                            app.startAnimation();
                            app.updateStatus("🎣 Testing animation - Double-click again to stop", false);
                        } else {
                            app.isRunning = false;
                            app.stopAnimation();
                            app.updateStatus("🛠️ Bot Ready - Click Start to begin", false);
                        }
                    }
                }
            });
        });
    }
}
