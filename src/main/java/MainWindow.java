import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

public class MainWindow {
    private static JFrame frame;
    private static JTextField searchField;
    private static JPanel clipsPanel;
    private static JPanel leftSidebar;
    private static JScrollPane clipsScrollPane;
    private static JTextArea detailViewer;
    private static JScrollPane detailScrollPane;
    private static boolean isWindowCreated = false;
    private static boolean isWindowVisible = false;
    private static Point initialClick;
    
    // Configuration
    private static int retentionDays = 10;
    private static Path dataDirectory;
    private static Preferences prefs;
    
    // Current state
    private static LocalDate currentDate = LocalDate.now();
    private static Map<LocalDate, List<ClipEntry>> dateClipsMap = new HashMap<>();
    private static boolean settingsDialogOpen = false;

    public static void showWindow() {
        if (!isWindowCreated) {
            createWindow();
            isWindowCreated = true;
            isWindowVisible = true;
        }
        
        // Make sure the window is properly brought to front
        frame.setVisible(true);
        frame.setExtendedState(JFrame.NORMAL);
        
        // Set always on top temporarily to ensure it comes to front
        frame.setAlwaysOnTop(true);
        frame.toFront();
        frame.requestFocus();
        
        isWindowVisible = true;
        refreshClips(currentDate);
        
        // Force focus to the search field
        searchField.requestFocusInWindow();
        searchField.selectAll();
        
        // After a short delay, set always on top to false to avoid issues with other apps
        Timer focusTimer = new Timer(300, e -> {
            frame.setAlwaysOnTop(false);
        });
        focusTimer.setRepeats(false);
        focusTimer.start();
    }

    public static void hideWindow() {
        if (frame != null) {
            frame.setAlwaysOnTop(false);
            frame.setVisible(false);
            isWindowVisible = false;
        }
    }

    private static void createWindow() {
        // Load configuration
        prefs = Preferences.userRoot().node("clipboardapp");
        retentionDays = prefs.getInt("retentionDays", 10);
        
        // Set up data directory
        String userHome = System.getProperty("user.home");
        dataDirectory = Paths.get(userHome, ".clipboardapp");
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                System.err.println("Failed to create data directory: " + e.getMessage());
            }
        }
        
        // Load existing data
        loadHistory();
        
        // Create a completely frameless window
        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(0, 0));
        
        // Create the main content panel with rounded corners
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(45, 45, 48));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
            }
        };
        mainPanel.setLayout(new BorderLayout(0, 0));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainPanel.setOpaque(false);
        
        // Create title bar for dragging
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(30, 30, 30));
        titleBar.setPreferredSize(new Dimension(frame.getWidth(), 40));
        titleBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // Add drag functionality
        titleBar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
            }
        });
        
        titleBar.addMouseMotionListener(new MouseAdapter() {
            public void mouseDragged(MouseEvent e) {
                // Get the location of the window
                int thisX = frame.getLocation().x;
                int thisY = frame.getLocation().y;
                
                // Determine how much the mouse moved since the initial click
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                
                // Move the window to this position
                int X = thisX + xMoved;
                int Y = thisY + yMoved;
                frame.setLocation(X, Y);
            }
        });
        
        // Add title
        JLabel titleLabel = new JLabel("Clipboard History");
        titleLabel.setForeground(new Color(220, 220, 220));
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        
        // Add close button
        JButton closeButton = new JButton("×");
        closeButton.setContentAreaFilled(false);
        closeButton.setBorderPainted(false);
        closeButton.setFocusPainted(false);
        closeButton.setForeground(new Color(200, 200, 200));
        closeButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
        closeButton.setPreferredSize(new Dimension(30, 30));
        closeButton.addActionListener(e -> hideWindow());
        closeButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                closeButton.setForeground(Color.WHITE);
                closeButton.setBackground(new Color(232, 17, 35));
            }
            public void mouseExited(MouseEvent e) {
                closeButton.setForeground(new Color(200, 200, 200));
                closeButton.setBackground(null);
            }
        });
        
        titleBar.add(titleLabel, BorderLayout.WEST);
        titleBar.add(closeButton, BorderLayout.EAST);
        
        // Create the search bar with modern styling
        searchField = new JTextField();
        searchField.setBackground(new Color(60, 60, 60));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80), 1, true),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        searchField.putClientProperty("JTextField.placeholderText", "Search across all history...");
        
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { performSearch(); }
            public void removeUpdate(DocumentEvent e) { performSearch(); }
            public void insertUpdate(DocumentEvent e) { performSearch(); }
        });
        
        // Create the left sidebar with modern styling
        leftSidebar = new JPanel();
        leftSidebar.setLayout(new BoxLayout(leftSidebar, BoxLayout.Y_AXIS));
        leftSidebar.setBackground(new Color(50, 50, 50));
        leftSidebar.setPreferredSize(new Dimension(180, 0));
        leftSidebar.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Create the clips panel with modern styling
        clipsPanel = new JPanel();
        clipsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 15, 15));
        clipsPanel.setBackground(new Color(45, 45, 48));
        
        clipsScrollPane = new JScrollPane(clipsPanel);
        clipsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        clipsScrollPane.getViewport().setBackground(new Color(45, 45, 48));
        clipsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        clipsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Custom scroll bar UI
        JScrollBar verticalScrollBar = clipsScrollPane.getVerticalScrollBar();
        verticalScrollBar.setUI(new ModernScrollBarUI());
        verticalScrollBar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        verticalScrollBar.setBackground(new Color(45, 45, 48));
        verticalScrollBar.setForeground(new Color(100, 100, 100));
        
        // Create the detail viewer with modern styling
        detailViewer = new JTextArea();
        detailViewer.setEditable(false);
        detailViewer.setBackground(new Color(60, 60, 60));
        detailViewer.setForeground(Color.WHITE);
        detailViewer.setLineWrap(true);
        detailViewer.setWrapStyleWord(true);
        detailViewer.setFont(new Font("Consolas", Font.PLAIN, 14));
        detailViewer.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        detailScrollPane = new JScrollPane(detailViewer);
        detailScrollPane.setPreferredSize(new Dimension(300, 0));
        detailScrollPane.setBorder(BorderFactory.createEmptyBorder());
        detailScrollPane.getViewport().setBackground(new Color(60, 60, 60));
        
        // Custom scroll bar UI for detail viewer
        JScrollBar detailScrollBar = detailScrollPane.getVerticalScrollBar();
        detailScrollBar.setUI(new ModernScrollBarUI());
        detailScrollBar.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
        
        // Build the UI
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBackground(new Color(45, 45, 48));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(45, 45, 48));
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        topPanel.add(searchField, BorderLayout.CENTER);
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(clipsScrollPane, BorderLayout.CENTER);
        centerPanel.add(detailScrollPane, BorderLayout.EAST);
        
        contentPanel.add(topPanel, BorderLayout.NORTH);
        contentPanel.add(leftSidebar, BorderLayout.WEST);
        contentPanel.add(centerPanel, BorderLayout.CENTER);
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        frame.add(titleBar, BorderLayout.NORTH);
        frame.add(mainPanel, BorderLayout.CENTER);
        
        // Add shadow effect
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.getRootPane().setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Add focus listener to hide when focus is lost
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                if (settingsDialogOpen) return;

                Timer timer = new Timer(100, event -> {
                    if (!frame.isFocusOwner() && !searchField.isFocusOwner()) {
                        hideWindow();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
        
        // Build the sidebar
        refreshSidebar();
    }
    
    private static void refreshSidebar() {
        leftSidebar.removeAll();
        
        // Add title to sidebar
        JLabel titleLabel = new JLabel("HISTORY");
        titleLabel.setForeground(new Color(180, 180, 180));
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        leftSidebar.add(titleLabel);
        
        // Add today button
        JButton todayButton = createSidebarButton("Today", LocalDate.now());
        leftSidebar.add(todayButton);
        leftSidebar.add(Box.createRigidArea(new Dimension(0, 10)));
        
        // Add section title
        JLabel datesLabel = new JLabel("PREVIOUS DAYS");
        datesLabel.setForeground(new Color(180, 180, 180));
        datesLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        datesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        datesLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        leftSidebar.add(datesLabel);
        
        // Add date buttons
        List<LocalDate> dates = new ArrayList<>(dateClipsMap.keySet());
        dates.sort(Collections.reverseOrder());
        
        for (LocalDate date : dates) {
            if (!date.equals(LocalDate.now())) {
                JButton dateButton = createSidebarButton(
                    date.format(DateTimeFormatter.ofPattern("E, MMM d")), date);
                leftSidebar.add(dateButton);
                leftSidebar.add(Box.createRigidArea(new Dimension(0, 5)));
            }
        }
        
        // Add settings button at the bottom
        leftSidebar.add(Box.createVerticalGlue());
        JButton settingsButton = createSidebarButton("Settings", null);
        settingsButton.addActionListener(e -> showSettings());
        leftSidebar.add(settingsButton);
        
        leftSidebar.revalidate();
        leftSidebar.repaint();
    }
    
    private static JButton createSidebarButton(String text, LocalDate date) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(160, 40));
        button.setBackground(new Color(60, 60, 60));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        
        if (date != null) {
            button.addActionListener(e -> refreshClips(date));
        }
        
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(70, 70, 70));
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(60, 60, 60));
            }
        });
        
        return button;
    }
    
    private static void refreshClips(LocalDate date) {
        currentDate = date;
        clipsPanel.removeAll();
        
        List<ClipEntry> clips = dateClipsMap.getOrDefault(date, new ArrayList<>());
        for (ClipEntry entry : clips) {
            ClipCard card = new ClipCard(entry);
            clipsPanel.add(card);
        }
        
        clipsPanel.revalidate();
        clipsPanel.repaint();
    }
    
    private static void performSearch() {
        String query = searchField.getText().toLowerCase();
        if (query.isEmpty()) {
            refreshClips(currentDate);
            return;
        }
        
        clipsPanel.removeAll();
        
        // Search across all dates
        for (LocalDate date : dateClipsMap.keySet()) {
            for (ClipEntry entry : dateClipsMap.get(date)) {
                if (entry.text.toLowerCase().contains(query)) {
                    ClipCard card = new ClipCard(entry);
                    clipsPanel.add(card);
                }
            }
        }
        
        clipsPanel.revalidate();
        clipsPanel.repaint();
    }
    
    private static void showSettings() {
        settingsDialogOpen = true;

        JDialog settingsDialog = new JDialog(frame, "Settings", true);
        settingsDialog.setUndecorated(true);
        settingsDialog.setSize(300, 200);
        settingsDialog.setLocationRelativeTo(frame);
        settingsDialog.getContentPane().setBackground(new Color(50, 50, 50));
        
        // Add window listener to track when dialog closes
        settingsDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                settingsDialogOpen = false;
                frame.requestFocus(); // Return focus to main window
            }
        });
        

        // Create a rounded panel for the dialog
        JPanel dialogPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(60, 60, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
            }
        };
        dialogPanel.setLayout(new BorderLayout());
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        dialogPanel.setOpaque(false);
        
        JPanel contentPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        contentPanel.setOpaque(false);
        
        JLabel retentionLabel = new JLabel("Retention Days:");
        retentionLabel.setForeground(Color.WHITE);
        retentionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        JSpinner retentionSpinner = new JSpinner(new SpinnerNumberModel(retentionDays, 1, 365, 1));
        retentionSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        JButton saveButton = new JButton("Save");
        saveButton.setBackground(new Color(70, 130, 180));
        saveButton.setForeground(Color.WHITE);
        saveButton.setFocusPainted(false);
        saveButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(new Color(100, 100, 100));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        contentPanel.add(retentionLabel);
        contentPanel.add(retentionSpinner);
        contentPanel.add(saveButton);
        contentPanel.add(cancelButton);
        
        saveButton.addActionListener(e -> {
            retentionDays = (Integer) retentionSpinner.getValue();
            prefs.putInt("retentionDays", retentionDays);
            cleanupOldFiles();
            settingsDialog.dispose();
        });
        
        cancelButton.addActionListener(e -> settingsDialog.dispose());
        
        dialogPanel.add(contentPanel, BorderLayout.CENTER);
        settingsDialog.add(dialogPanel);
        // Make sure settings dialog gets focus
        settingsDialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                retentionSpinner.requestFocus();
            }
        });
        settingsDialog.setVisible(true);
    }
    
    private static void loadHistory() {
        dateClipsMap.clear();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirectory, "*.txt")) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                String dateStr = filename.substring(0, filename.lastIndexOf('.'));
                
                try {
                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
                    List<ClipEntry> clips = new ArrayList<>();
                    
                    // Read all lines from the file
                    List<String> lines = Files.readAllLines(file);
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            clips.add(new ClipEntry(line, date));
                        }
                    }
                    
                    dateClipsMap.put(date, clips);
                } catch (Exception e) {
                    System.err.println("Error reading file: " + filename + ", " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading data directory: " + e.getMessage());
        }
        
        // Ensure current date is in the map
        if (!dateClipsMap.containsKey(currentDate)) {
            dateClipsMap.put(currentDate, new ArrayList<>());
        }
    }
    
    public static void addClip(String text) {
        if (text == null || text.trim().isEmpty()) return;
    
        // Get today's clips
        List<ClipEntry> todayClips = dateClipsMap.get(currentDate);
        if (todayClips == null) {
            todayClips = new ArrayList<>();
            dateClipsMap.put(currentDate, todayClips);
        }
        
        // Avoid duplicates
        if (!todayClips.isEmpty() && todayClips.get(0).text.equals(text)) return;
        
        // Add to beginning of list
        todayClips.add(0, new ClipEntry(text, LocalDate.now()));
        
        // Save to file
        saveToFile(currentDate, todayClips);
        
        // Refresh UI if visible
        if (isWindowVisible && currentDate.equals(LocalDate.now())) {
            refreshClips(currentDate);
        }
    }
    
    private static void saveToFile(LocalDate date, List<ClipEntry> clips) {
        if (date == null) {
            date = LocalDate.now();
        }
        
        Path filePath = dataDirectory.resolve(date.format(DateTimeFormatter.ISO_DATE) + ".txt");
        
        try {
            // Save only the text content, not the date
            List<String> textContents = new ArrayList<>();
            for (ClipEntry entry : clips) {
                textContents.add(entry.text);
            }
            Files.write(filePath, textContents);
        } catch (IOException e) {
            System.err.println("Error saving to file: " + e.getMessage());
        }
    }
    
    private static void cleanupOldFiles() {
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirectory, "*.txt")) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                String dateStr = filename.substring(0, filename.lastIndexOf('.'));
                
                try {
                    LocalDate fileDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
                    if (fileDate.isBefore(cutoff)) {
                        Files.delete(file);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing file: " + filename + ", " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error cleaning up files: " + e.getMessage());
        }
        
        // Reload history after cleanup
        loadHistory();
        refreshSidebar();
        if (isWindowVisible) {
            refreshClips(currentDate);
        }
    }
    
    public static boolean isWindowVisible() {
        return isWindowVisible && frame != null && frame.isShowing();
    }
    
    public static void bringToFront() {
        if (frame != null) {
            if (!isWindowVisible) {
                showWindow();
            } else {
                frame.setExtendedState(JFrame.NORMAL);
                frame.toFront();
                frame.requestFocus();
                searchField.requestFocusInWindow();
                frame.setAlwaysOnTop(true);

                 // After a short delay, set always on top to false
                Timer focusTimer = new Timer(500, e -> {
                    frame.setAlwaysOnTop(false);
                });

                focusTimer.setRepeats(false);
                focusTimer.start();
            }
        }
    }
    
    // Custom card component for each clip
    static class ClipCard extends JPanel {
        private ClipEntry entry;
        
        public ClipCard(ClipEntry entry) {
            this.entry = entry;
            setLayout(new BorderLayout());
            setBackground(new Color(60, 60, 60));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80), 1, true),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
            ));
            setPreferredSize(new Dimension(220, 160)); // Slightly wider to accommodate code
            
            // Main content panel
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setOpaque(false);
            
            // Truncated text label with improved font and better HTML handling
            JLabel textLabel = new JLabel("<html><div style='width:190px; height:110px;'>" + 
                truncateText(entry.text, 6) + "</div></html>");
            textLabel.setForeground(Color.WHITE);
            textLabel.setFont(new Font("Consolas", Font.PLAIN, 12)); // Use monospaced font for code
            textLabel.setToolTipText(entry.text);
            textLabel.setVerticalAlignment(SwingConstants.TOP);
            textLabel.setOpaque(false);
            
            // Date label at the bottom
            JLabel dateLabel = new JLabel(formatDate(entry.date));
            dateLabel.setForeground(new Color(180, 180, 180));
            dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            dateLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            dateLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
            
            contentPanel.add(textLabel, BorderLayout.CENTER);
            contentPanel.add(dateLabel, BorderLayout.SOUTH);
            
            add(contentPanel, BorderLayout.CENTER);
            
            // Add mouse listener to the entire panel
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    copyToClipboard();
                    detailViewer.setText(entry.text);
                }
                
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(new Color(70, 70, 70));
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(90, 90, 90), 1, true),
                        BorderFactory.createEmptyBorder(15, 15, 15, 15)
                    ));
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(new Color(60, 60, 60));
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(80, 80, 80), 1, true),
                        BorderFactory.createEmptyBorder(15, 15, 15, 15)
                    ));
                }
            });
            
            // Also add the mouse listener to the text label to ensure it's clickable
            textLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    copyToClipboard();
                    detailViewer.setText(entry.text);
                }
                
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(new Color(70, 70, 70));
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(90, 90, 90), 1, true),
                        BorderFactory.createEmptyBorder(15, 15, 15, 15)
                    ));
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(new Color(60, 60, 60));
                    setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(80, 80, 80), 1, true),
                        BorderFactory.createEmptyBorder(15, 15, 15, 15)
                    ));
                }
            });
        }
        
        private String truncateText(String text, int maxLines) {
            // Remove extra whitespace at the beginning of lines (common in code)
            text = text.replaceAll("(?m)^\\s+", "");
            
            // First, handle line breaks
            String[] lines = text.split("\n");
            if (lines.length <= maxLines && text.length() < 150) {
                return text.replace("\n", "<br>").replace(" ", "&nbsp;");
            }
            
            // If too many lines or too long, truncate and add ellipsis
            StringBuilder truncated = new StringBuilder();
            int totalChars = 0;
            int linesAdded = 0;
            
            for (int i = 0; i < lines.length && linesAdded < maxLines; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                
                if (totalChars + line.length() > 150) {
                    // If we're getting too long, truncate this line
                    int remaining = 150 - totalChars - 3; // Reserve 3 chars for ellipsis
                    if (remaining > 10) { // Only truncate if we have meaningful content
                        truncated.append(escapeHtml(line.substring(0, remaining))).append("...");
                        linesAdded++;
                    }
                    break;
                }
                
                if (linesAdded > 0) {
                    truncated.append("<br>");
                }
                
                truncated.append(escapeHtml(line));
                totalChars += line.length();
                linesAdded++;
            }
            
            if (lines.length > maxLines || text.length() > 150) {
                truncated.append("<br>...");
            }
            
            return truncated.toString();
        }
        
        // Helper method to escape HTML and preserve spaces
        private String escapeHtml(String text) {
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace(" ", "&nbsp;");
        }
        
        private String formatDate(LocalDate date) {
            return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
        }
        
        private void copyToClipboard() {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(entry.text), null);
        }
    }
    
    // Custom scroll bar UI
    static class ModernScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(100, 100, 100);
            this.trackColor = new Color(45, 45, 48);
        }
        
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createInvisibleButton();
        }
        
        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createInvisibleButton();
        }
        
        private JButton createInvisibleButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
        
        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, 5, 5);
            g2.dispose();
        }
        
        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(trackColor);
            g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
            g2.dispose();
        }
    }
    
    // Custom layout manager for wrapping grid
    static class WrapLayout extends FlowLayout {
        public WrapLayout() {
            super();
        }
        
        public WrapLayout(int align) {
            super(align);
        }
        
        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }
        
        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }
        
        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }
        
        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }
                
                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);
                
                int x = 0;
                int y = insets.top + vgap;
                int rowHeight = 0;
                int maxX = 0;
                
                for (Component comp : target.getComponents()) {
                    if (comp.isVisible()) {
                        Dimension dim = preferred ? comp.getPreferredSize() : comp.getMinimumSize();
                        
                        if (x == 0 || x + dim.width <= maxWidth) {
                            if (x > 0) {
                                x += hgap;
                            }
                            x += dim.width;
                            rowHeight = Math.max(rowHeight, dim.height);
                        } else {
                            x = dim.width;
                            y += vgap + rowHeight;
                            rowHeight = dim.height;
                        }
                        maxX = Math.max(maxX, x);
                    }
                }
                
                y += rowHeight;
                
                return new Dimension(maxX + insets.right, y + insets.bottom);
            }
        }
    }

    static {
        // Set the focus traversal policy to ensure components can receive focus
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
            @Override
            public boolean dispatchEvent(AWTEvent e) {
                if (e instanceof KeyEvent) {
                    KeyEvent ke = (KeyEvent) e;
                    if (ke.getID() == KeyEvent.KEY_PRESSED && 
                        ke.getKeyCode() == KeyEvent.VK_ESCAPE && 
                        isWindowVisible) {
                        hideWindow();
                        return true;
                    }
                }
                return super.dispatchEvent(e);
            }
        });
    }

    public static class ClipEntry {
        public final String text;
        public final LocalDate date;

        public ClipEntry(String text, LocalDate date) {
            this.text = text;
            this.date = date;
        }
    }
}