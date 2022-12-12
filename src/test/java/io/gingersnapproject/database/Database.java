package io.gingersnapproject.database;

import io.fabric8.kubernetes.api.model.Secret;
import jakarta.persistence.TypedQuery;

import java.util.List;

public interface Database {
    Secret connectionSecret(String name);

    void insert(Object entity);

    void delete(Object entity);

    <T> TypedQuery<T> query(String query, Class<T> resultClass);
}
