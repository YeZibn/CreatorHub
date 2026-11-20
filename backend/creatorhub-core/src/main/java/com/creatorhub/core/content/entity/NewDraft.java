package com.creatorhub.core.content.entity;

public class NewDraft {
    private long id;
    private final long authorId;
    private final String title;
    private final String body;

    public NewDraft(long authorId, String title, String body) {
        this.authorId = authorId;
        this.title = title;
        this.body = body;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }
}
