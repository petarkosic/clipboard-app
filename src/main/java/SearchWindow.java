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
        frame = new JFrame("Clipboard Search");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setSize(400, 500);
        frame.setLocationRelativeTo(null);

        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { updateSearch(); }
            public void removeUpdate(DocumentEvent e) { updateSearch(); }
            public void insertUpdate(DocumentEvent e) { updateSearch(); }
        });
        
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setCellRenderer(new HighlightRenderer());
        resultList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 1) {
                    copySelectedToClipboard();
                    frame.setVisible(false);
                }
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
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
            
            String highlighted = result.text.substring(0, result.highlightStart) +
                "<span style='background:yellow;'>" +
                result.text.substring(result.highlightStart, result.highlightEnd) +
                "</span>" +
                result.text.substring(result.highlightEnd);
            
            pane.setText("<html><body style='white-space:pre-wrap;font-family:" + 
                getFont().getFamily() + ";'>" + highlighted + "</body></html>");
            
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