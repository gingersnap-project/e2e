package io.gingersnapproject.database;

import io.fabric8.kubernetes.api.model.Secret;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Set;

public interface Database {
    Secret connectionSecret(String name);

    String select(Set<String> valueColumns, String table, List<String> whereColumns);

    void insert(Object entity);

    void delete(Object entity);

    <T> TypedQuery<T> query(String query, Class<T> resultClass);
}
