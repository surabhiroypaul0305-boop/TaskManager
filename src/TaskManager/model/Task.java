package TaskManager.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Task {
    private final int id;
    private final String title;
    private final String description;
    private final boolean completed;
    private final String dueDate;
    private final Priority priority;
    private final Integer parentId;
    private final long createdAt;

    private Task(TaskBuilder b) {
        this.id = b.id;
        this.title = b.title;
        this.description = b.description;
        this.completed = b.completed;
        this.dueDate = b.dueDate;
        this.priority = b.priority;
        this.parentId = b.parentId;
        this.createdAt = b.createdAt == null ? System.currentTimeMillis() : b.createdAt;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getDueDate() {
        return dueDate;
    }

    public Priority getPriority() {
        return priority;
    }

    public Integer getParentId() {
        return parentId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public TaskBuilder toBuilder() {
        return new TaskBuilder(title, description, completed)
                .id(id).dueDate(dueDate).priority(priority)
                .parentId(parentId).createdAt(createdAt);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", id);
        fields.put("title", title);
        fields.put("description", description);
        fields.put("completed", completed);
        fields.put("dueDate", dueDate);
        fields.put("priority", priority == null ? null : priority.name());
        fields.put("parentId", parentId);
        fields.put("createdAt", createdAt);
        return fields;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", completed=" + completed +
                ", dueDate='" + dueDate + '\'' +
                ", priority=" + priority +
                ", parentId=" + parentId +
                ", createdAt=" + createdAt +
                '}';
    }

    public static class TaskBuilder {
        private  int  id;
        private final String title;
        private final String description;
        private final boolean completed;
        private  String dueDate;
        private  Priority priority = Priority.MEDIUM;
        private  Integer parentId;
        private Long createdAt;

        public TaskBuilder(String title, String description, boolean completed) {
            this.title = title;
            this.description = description;
            this.completed = completed;
        }
        public TaskBuilder id(int id) {
            this.id = id;
            return this;
        }
        public TaskBuilder dueDate(String dueDate){
            this.dueDate = dueDate;
            return this;
        }
        public TaskBuilder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public TaskBuilder parentId(Integer parentId) {
            this.parentId = parentId;
            return this;
        }
        public TaskBuilder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        public String getDueDate() {
            return dueDate;
        }

        public Task build(){
            if(title == null || title.isBlank()){
                throw new IllegalArgumentException("Title is required");
            }
            if(description == null || description.isBlank()){
                throw new IllegalArgumentException("Description is required");
            }

            return  new Task(this);
        }

    }
}
