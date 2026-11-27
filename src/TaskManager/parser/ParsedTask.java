package TaskManager.parser;

/** Parser output, not a validated or persisted task. */
public record ParsedTask(String title, String description, String dueDate, String recurrence) {}
