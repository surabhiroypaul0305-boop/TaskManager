package TaskManager.parser;

@FunctionalInterface
public interface TaskParser {
    ParsedTask parse(String text, String today);
}
