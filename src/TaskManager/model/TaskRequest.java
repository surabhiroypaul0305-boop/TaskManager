package TaskManager.model;

public final class TaskRequest {
    private final String title;
    private final String description;
    private final String completed;
    private final String dueDate;
    private final String priority;

    public TaskRequest(String title, String description, String completed, String dueDate, String priority) {
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.dueDate = dueDate;
        this.priority = priority;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCompleted() {
        return completed;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getPriority() {
        return priority;
    }
}
