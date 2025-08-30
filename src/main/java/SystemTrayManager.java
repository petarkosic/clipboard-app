import com.github.kwhat.jnativehook.GlobalScreen;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

public class SystemTrayManager {
    private static final Set<Integer> pressedKeys = new HashSet<>();
    private static final int HOTKEY_MOD1 = KeyEvent.VK_CONTROL;
    private static final int HOTKEY_MOD2 = KeyEvent.VK_SHIFT;
    private static final int HOTKEY_KEY = KeyEvent.VK_V;

    public void init() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray not supported!");
            return;
        }

        TrayIcon trayIcon = new TrayIcon(createDefaultIcon(), "Clipboard Manager");
        SystemTray tray = SystemTray.getSystemTray();

        PopupMenu popup = new PopupMenu();
        MenuItem openItem = new MenuItem("Open");
        openItem.addActionListener(e -> MainWindow.showWindow());
        MenuItem exitItem = new MenuItem("Exit");

        exitItem.addActionListener(e -> {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            System.exit(0);
        });
        
        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);
        trayIcon.setPopupMenu(popup);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(new KeyEventDispatcher() {
                @Override
                public boolean dispatchKeyEvent(KeyEvent e) {
                    synchronized (SystemTrayManager.class) {
                        if (e.getID() == KeyEvent.KEY_PRESSED) {
                            pressedKeys.add(e.getKeyCode());
                        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                            pressedKeys.remove(e.getKeyCode());
                        }

                        if (pressedKeys.contains(HOTKEY_MOD1) && 
                            pressedKeys.contains(HOTKEY_MOD2) && 
                            pressedKeys.contains(HOTKEY_KEY)) {
                            MainWindow.showWindow();
                            pressedKeys.clear();
                            
                            return true;
                        }
                    }
                    
                    return false;
                }
            });
    }

    private Image createDefaultIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillOval(0, 0, 16, 16);
        g.dispose();

        return image;
    }
}