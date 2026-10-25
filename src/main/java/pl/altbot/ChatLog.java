package pl.altbot;

import java.util.ArrayList;
import java.util.List;

/** Ostatnie wiadomosci z czatu, pokazywane w zakladce "Czat". */
public final class ChatLog {
    private static final int MAX = 60;
    private static final List<String> LINES = new ArrayList<>();

    private ChatLog() {}

    public static synchronized void add(String line) {
        if (line == null || line.isBlank()) return;
        LINES.add(line.replace('\n', ' '));
        while (LINES.size() > MAX) LINES.remove(0);
    }

    /** Ostatnie {@code count} linii (najnowsze na koncu). */
    public static synchronized List<String> last(int count) {
        int from = Math.max(0, LINES.size() - count);
        return new ArrayList<>(LINES.subList(from, LINES.size()));
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
