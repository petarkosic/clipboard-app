import java.util.*;
import java.util.stream.Collectors;

public class HistoryManager {
    private static HistoryManager instance;
    private List<String> history = new ArrayList<>();
    private int maxHistory = 100;

    public static HistoryManager getInstance() {
        if (instance == null) {
            instance = new HistoryManager();
        }

        return instance;
    }

    public void addEntry(String text) {
        if (text == null || text.trim().isEmpty()) return;

        if (!history.isEmpty()) {
            String last = history.get(0);

            if (last.equals(text)) return;
            if (history.size() > 1 && history.get(1).equals(text)) {
                history.remove(1);
            }
        }

        history.remove(text);
        history.add(0, text);

        if (history.size() > maxHistory) {
            history = new ArrayList<>(history.subList(0, maxHistory));
        }
    }

    public List<SearchResult> search(String query) {
        return history.stream()
            .filter(item -> item.toLowerCase().contains(query.toLowerCase()))
            .map(item -> {
                int start = item.toLowerCase().indexOf(query.toLowerCase());
                return new SearchResult(item, start, start + query.length());
            })
            .collect(Collectors.toList());
    }

    public static class SearchResult {
        public final String text;
        public final int highlightStart;
        public final int highlightEnd;

        public SearchResult(String text, int highlightStart, int highlightEnd) {
            this.text = text;
            this.highlightStart = highlightStart;
            this.highlightEnd = highlightEnd;
        }
    }
}