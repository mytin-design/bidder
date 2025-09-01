package bot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BidderApp extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField bidTextField;
    private JLabel foundOrdersLabel;
    private JLabel successfulBidsLabel;
    private JButton startButton;
    private JButton stopButton;
    private JTextArea logArea;
    
    private BidderBot bot;
    private boolean isRunning = false;
    
    public BidderApp() {
        initializeGUI();
    }
    
    private void initializeGUI() {
        setTitle("StudyBay Bidder Bot");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Top panel - Login credentials
        JPanel loginPanel = createLoginPanel();
        
        // Middle panel - Statistics
        JPanel statsPanel = createStatsPanel();
        
        // Bottom panel - Controls and log
        JPanel controlPanel = createControlPanel();
        
        mainPanel.add(loginPanel, BorderLayout.NORTH);
        mainPanel.add(statsPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Login Credentials"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Username
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        usernameField = new JTextField(20);
        panel.add(usernameField, gbc);
        
        // Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        passwordField = new JPasswordField(20);
        panel.add(passwordField, gbc);
        
        // Bid Text
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Bid Text:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        bidTextField = new JTextField("Hi. Why don't you hire me, relax and I will get it done for you within no time?", 20);
        panel.add(bidTextField, gbc);
        
        return panel;
    }
    
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Statistics"));
        
        panel.add(new JLabel("Found Orders:"));
        foundOrdersLabel = new JLabel("0");
        foundOrdersLabel.setFont(foundOrdersLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(foundOrdersLabel);
        
        panel.add(new JLabel("Successful Bids:"));
        successfulBidsLabel = new JLabel("0");
        successfulBidsLabel.setFont(successfulBidsLabel.getFont().deriveFont(Font.BOLD, 16f));
        successfulBidsLabel.setForeground(Color.GREEN.darker());
        panel.add(successfulBidsLabel);
        
        return panel;
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        startButton = new JButton("Start Bot");
        startButton.setBackground(Color.GREEN);
        startButton.setForeground(Color.WHITE);
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startBot();
            }
        });
        
        stopButton = new JButton("Stop Bot");
        stopButton.setBackground(Color.RED);
        stopButton.setForeground(Color.WHITE);
        stopButton.setEnabled(false);
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopBot();
            }
        });
        
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        
        // Log area
        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        
        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void startBot() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String bidText = bidTextField.getText().trim();
        
        if (username.isEmpty() || password.isEmpty() || bidText.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please fill in all fields before starting the bot.", 
                "Missing Information", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        isRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        
        bot = new BidderBot(username, password, bidText, this);
        
        // Start bot in separate thread
        Thread botThread = new Thread(() -> {
            try {
                bot.start();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    logMessage("Error: " + e.getMessage());
                    stopBot();
                });
            }
        });
        botThread.setDaemon(true);
        botThread.start();
        
        logMessage("Bot started successfully!");
    }
    
    private void stopBot() {
        isRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        
        if (bot != null) {
            bot.stop();
            bot = null;
        }
        
        logMessage("Bot stopped.");
    }
    
    public void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
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
            new BidderApp().setVisible(true);
        });
    }
}
