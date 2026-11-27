package TaskManager.parser;

import java.time.LocalDate;
import java.util.Locale;

public final class RuleBasedTaskParser implements TaskParser {
    @Override
    public ParsedTask parse(String text, String today) {
        String title = text.trim();
        String words = " " + title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        LocalDate date = LocalDate.parse(today);
        boolean monthly = words.contains(" every month ");
        LocalDate due = null;
        if (words.contains(" tomorrow ")) due = date.plusDays(1);
        else if (words.contains(" 1st ")) due = date.plusMonths(1).withDayOfMonth(1);
        else if (monthly) due = date.plusMonths(1);
        return new ParsedTask(title, title, due == null ? null : due.toString(), monthly ? "monthly" : null);
    }
}
