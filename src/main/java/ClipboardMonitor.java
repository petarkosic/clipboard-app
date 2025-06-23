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

    public void start() {
        clipboard.addFlavorListener(this);
    }

    @Override
    public void flavorsChanged(FlavorEvent e) {
        if (!isProcessing.compareAndSet(false, true)) {
            return;
        };

        try {
            int retries = 3;
            
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
                    }
                    break; // Success - exit retry loop
                } catch (IllegalStateException ex) {
                    if (retries == 0) {
                        System.err.println("Failed to access clipboard after retries");
                        ex.printStackTrace();
                    }
                    
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    } // Wait before retrying
                    
                } catch (Exception ex) {
                    ex.printStackTrace();
                    break;
                }
            }
        } finally {
            isProcessing.set(false);
        }
    }
}