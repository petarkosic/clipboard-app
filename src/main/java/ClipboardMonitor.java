import java.awt.*;
import java.awt.datatransfer.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

public class ClipboardMonitor implements FlavorListener {
    private Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private AtomicBoolean isProcessing = new AtomicBoolean(false);

    public ClipboardMonitor() {
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    public void start() {
        clipboard.addFlavorListener(this);
        System.out.println("Clipboard monitor started");
    }

    @Override
    public void flavorsChanged(FlavorEvent e) {
        if (!isProcessing.compareAndSet(false, true)) {
            System.out.println("Clipboard change already being processed, skipping...");
            return;
        }

        try {
            int retries = 5;
            while (retries-- > 0) {
                try {
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                        System.out.println("Clipboard content captured: " + text);
                        
                        // Add to the main window storage
                        MainWindow.addClip(text);
                        
                        SwingUtilities.invokeLater(() -> {
                            if (MainWindow.isWindowVisible()) {
                                // Refresh will happen automatically through addClip
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
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } catch (Exception ex) {
                    System.err.println("Unexpected error accessing clipboard: " + ex.getMessage());
                    break;
                }
            }
        } finally {
            isProcessing.set(false);
        }
    }
}