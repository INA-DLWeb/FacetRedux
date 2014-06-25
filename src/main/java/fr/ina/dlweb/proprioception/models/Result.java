package fr.ina.dlweb.proprioception.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Date;

/**
 * Date: 07/12/12
 * Time: 15:21
 *
 * @author drapin
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="class")
public abstract class Result implements Comparable<Result> {
    private final int id;
    private final String name;
    private final String query;

    private final Date added;
    private final Date started;
    private final Date done;

    protected Result(int id, String name, String query, Date added, Date started, Date done) {
        this.id = id;
        this.name = name;
        this.query = query;
        this.added = added;
        this.started = started;
        this.done = done;
    }

    public final String getQuery() {
        return query;
    }

    @JsonIgnore
    public final boolean isFailure() {
        return getClass().equals(Failure.class);
    }

    public final int getId() {
        return id;
    }

    public final String getName() {
        return name;
    }

    public final Date getAdded() {
        return added;
    }

    public final Date getStarted() {
        return started;
    }

    public final Date getDone() {
        return done;
    }

    @Override
    public int compareTo(Result o) {
        return new Integer(id).compareTo(o.getId());
    }
}
