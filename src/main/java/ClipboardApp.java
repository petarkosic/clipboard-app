import javax.swing.SwingUtilities;

import com.github.kwhat.jnativehook.GlobalScreen;

public class ClipboardApp {
    public static void main(String[] args) {
        SystemTrayManager trayManager = new SystemTrayManager();
        ClipboardMonitor monitor = new ClipboardMonitor();
        GlobalHotkeyManager hotkeyManager = new GlobalHotkeyManager();
        
        SwingUtilities.invokeLater(() -> {
            trayManager.init();
            monitor.start();
            hotkeyManager.start();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}