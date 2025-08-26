import java.awt.*;
import java.awt.datatransfer.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

public class ClipboardMonitor implements FlavorListener {
    private Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private HistoryManager historyManager = HistoryManager.getInstance();
    private AtomicBoolean isProcessing = new AtomicBoolean(false);

    public ClipboardMonitor() {
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        this.historyManager = HistoryManager.getInstance();
    }

    private void startPolling() {
        Thread pollThread = new Thread(() -> {
            String lastContent = null;

            while (true) {
                try {
                    Thread.sleep(200);

                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String currentContent = (String) clipboard.getData(DataFlavor.stringFlavor);
                    
                        if (currentContent != null && !currentContent.equals(lastContent)) {
                            lastContent = currentContent;
                            historyManager.addEntry(currentContent);
                    
                            SwingUtilities.invokeLater(() -> {
                                if (SearchWindow.isWindowVisible()) {
                                    SearchWindow.refreshResults();
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Polling error: " + e.getMessage());
                }
            }
        });
        
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void start() {
        clipboard.addFlavorListener(this);
        startPolling();
    }

    @Override
    public void flavorsChanged(FlavorEvent e) {
        if (!isProcessing.compareAndSet(false, true)) {
            System.out.println("Clipboard change already being processed, skipping...");
            return;
        }

        try {
            int retries = 5; // Increased retry attempts
            while (retries-- > 0) {
                try {
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                        historyManager.addEntry(text);
                        
                        SwingUtilities.invokeLater(() -> {
                            if (SearchWindow.isWindowVisible()) {
                                SearchWindow.refreshResults();
                            }
                        });
                    } else {
                        System.out.println("Clipboard contains non-text data, skipping.");
                    }
                    break;
                } catch (IllegalStateException ex) {
                    System.out.println("Clipboard locked, retries left: " + retries);

                    if (retries == 0) {
                        System.err.println("Failed to access clipboard after all retries");
                        ex.printStackTrace();
                    }
                    
                    try {
                        Thread.sleep(100); // Increased sleep time
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } catch (Exception ex) {
                    System.err.println("Unexpected error accessing clipboard: " + ex.getMessage());
                    ex.printStackTrace();
                    break;
                }
            }
        } finally {
            isProcessing.set(false);
        }
    }
}