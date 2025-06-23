import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;

public class SearchWindow {
    private static JFrame frame;
    private static JTextField searchField;
    private static JList<HistoryManager.SearchResult> resultList;
    private static DefaultListModel<HistoryManager.SearchResult> listModel;
    private static boolean isWindowCreated = false;
    private static boolean isWindowVisible = false;


    public static void showWindow() {
        if (!isWindowCreated) {
            createWindow();
            isWindowCreated = true;
            isWindowVisible = true;
        }
        
        frame.setVisible(true);
        frame.setExtendedState(JFrame.NORMAL);;
        frame.toFront();
        isWindowVisible = true;
        refreshResults();
        frame.requestFocus();
        
        searchField.setText("");
        updateSearch();
        searchField.requestFocusInWindow();
    }

    private static void createWindow() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            e.printStackTrace();
        }

        UIManager.put("control", new Color(43, 43, 43));
        UIManager.put("text", new Color(240, 240, 240));
        UIManager.put("nimbusBase", new Color(18, 30, 49));
        UIManager.put("nimbusAlertYellow", new Color(248, 187, 0));
        UIManager.put("nimbusDisabledText", new Color(140, 140, 140));
        UIManager.put("nimbusFocus", new Color(115, 164, 209));
        UIManager.put("nimbusGreen", new Color(176, 179, 50));
        UIManager.put("nimbusInfoBlue", new Color(66, 139, 221));
        UIManager.put("nimbusLightBackground", new Color(30, 30, 30));
        UIManager.put("nimbusOrange", new Color(191, 98, 4));
        UIManager.put("nimbusRed", new Color(169, 46, 34));
        UIManager.put("nimbusSelectedText", new Color(255, 255, 255));
        UIManager.put("nimbusSelectionBackground", new Color(0, 57, 107));
        UIManager.put("textBackground", new Color(30, 30, 30));
        UIManager.put("textForeground", new Color(240, 240, 240));

        frame = new JFrame("Clipboard Search");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setSize(400, 500);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(new Color(30, 30, 30));

        searchField = new JTextField();
        searchField.setBackground(new Color(43, 43, 43));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { updateSearch(); }
            public void removeUpdate(DocumentEvent e) { updateSearch(); }
            public void insertUpdate(DocumentEvent e) { updateSearch(); }
        });
        
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setBackground(new Color(30, 30, 30));
        resultList.setForeground(Color.WHITE);
        resultList.setSelectionBackground(new Color(0, 57, 107));
        resultList.setSelectionForeground(Color.WHITE);
        resultList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        resultList.setCellRenderer(new HighlightRenderer());
        resultList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 1) {
                    copySelectedToClipboard();
                    frame.setVisible(false);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(30, 30, 30));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 30, 30));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(searchField, BorderLayout.NORTH);
        panel.add(new JScrollPane(resultList), BorderLayout.CENTER);
        frame.add(panel);

        frame.getRootPane().registerKeyboardAction(
            e -> frame.setVisible(false),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                isWindowVisible = false;
            }

            @Override
            public void windowClosed(WindowEvent e) {
                isWindowVisible = false;
            }
        });
    }

    public static boolean isWindowVisible() {
        return isWindowVisible;
    }

    public static void bringToFront() {
        if (frame != null) {
            frame.setExtendedState(JFrame.NORMAL);
            frame.toFront();
            frame.requestFocus();
            refreshResults();
        }
    }

    public static void refreshResults() {
        if (frame != null) {
            updateSearch();
        }

        if (resultList.getModel().getSize() > 0) {
            resultList.setSelectedIndex(0);
            Timer timer = new Timer(300, e -> resultList.clearSelection());
            timer.setRepeats(false);
            timer.start();
        }
    }

    private static void updateSearch() {
        String query = searchField.getText();
        listModel.clear();
        HistoryManager.getInstance().search(query).forEach(listModel::addElement);
    }

    private static void copySelectedToClipboard() {
        HistoryManager.SearchResult selected = resultList.getSelectedValue();
        if (selected != null) {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(selected.text), null);
        }
    }

    private static class HighlightRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list, Object value, int index, 
            boolean isSelected, boolean cellHasFocus) {
            
            HistoryManager.SearchResult result = (HistoryManager.SearchResult) value;
            JTextPane pane = new JTextPane();
            pane.setContentType("text/html");
            pane.setBackground(isSelected ? new Color(0, 57, 107) : new Color(30, 30, 30));
            
            String highlighted = result.text.substring(0, result.highlightStart) +
                "<span style='background:yellow;'>" +
                result.text.substring(result.highlightStart, result.highlightEnd) +
                "</span>" +
                result.text.substring(result.highlightEnd);

            pane.setText("<html><body style='white-space:pre-wrap;font-family:" + 
            getFont().getFamily() + ";color:" + (isSelected ? "white" : "#E0E0E0") + "'>" + 
            highlighted + "</body></html>");
        
            pane.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        
            if (isSelected) {
                pane.setBackground(list.getSelectionBackground());
                pane.setForeground(list.getSelectionForeground());
            } else {
                pane.setBackground(list.getBackground());
                pane.setForeground(list.getForeground());
            }
            
            return pane;
        }
    }
}