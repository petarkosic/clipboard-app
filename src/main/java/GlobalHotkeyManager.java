import java.awt.EventQueue;

import javax.swing.SwingUtilities;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

public class GlobalHotkeyManager implements NativeKeyListener {
    private static final int HOTKEY_CTRL = NativeKeyEvent.VC_CONTROL;
    private static final int HOTKEY_SHIFT = NativeKeyEvent.VC_SHIFT;
    private static final int HOTKEY_ALT = NativeKeyEvent.VC_ALT;
    
    private boolean ctrlPressed = false;
    private boolean shiftPressed = false;

    public void start() {
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException ex) {
            System.err.println("Failed to register native hook:");
            ex.printStackTrace();
            return;
        }
        
        GlobalScreen.addNativeKeyListener(this);
        System.out.println("Global hotkey listener started");
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == HOTKEY_CTRL) ctrlPressed = true;
        if (e.getKeyCode() == HOTKEY_SHIFT) shiftPressed = true;
        
        if (ctrlPressed && shiftPressed && e.getKeyCode() == HOTKEY_ALT) {
            SwingUtilities.invokeLater(() -> {
                // Use invokeLater to ensure proper focus handling
                EventQueue.invokeLater(() -> {
                    if (!MainWindow.isWindowVisible()) {
                        MainWindow.showWindow();
                    } else {
                        MainWindow.bringToFront();
                    }
                });
            });
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        if (e.getKeyCode() == HOTKEY_CTRL) ctrlPressed = false;
        if (e.getKeyCode() == HOTKEY_SHIFT) shiftPressed = false;
    }
}