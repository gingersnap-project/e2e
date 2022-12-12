package io.gingersnapproject;

import io.gingersnap_project.v1alpha1.cachespec.DataSource;
import io.gingersnap_project.v1alpha1.eagercacherulestatus.Conditions;
import io.gingersnapproject.data.Customer;
import io.gingersnapproject.gingersnap.GingersnapClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.gingersnapproject.kubernetes.Util.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class AbstractEagerCacheTest extends AbstractTest {

    protected final DataSource.DbType dsType;

    public AbstractEagerCacheTest(DataSource.DbType dsType) {
        this.dsType = dsType;
    }

    @Test
    public void test() {
        String cacheName = "eager-cache";
        String connSecretName = "db-credential-secret";
        k8s.resource(db.connectionSecret(connSecretName))
                .inNamespace(namespace)
                .create();

        // Create Cache
        var cache = cache(dsType, cacheName, connSecretName);
        cache = createAndWaitForCache(cache);

        var ruleName = "e2e-rule";
        var rule = k8s.resource(
                        eagerCacheRule(
                                ruleName,
                                cache,
                                "gingersnap."+Customer.TABLE_NAME, // TODO abstract per DB
                                key -> key.setKeyColumns(Collections.singletonList("id")),
                                value -> value.setValueColumns(List.of("fullname", "email"))
                        ))
                .inNamespace(namespace)
                .create();

        // Wait for Rule to be Ready
        k8s.resource(rule)
                .inNamespace(namespace)
                .waitUntilCondition(r -> {
                    if (r.getStatus() == null || r.getStatus().getConditions() == null)
                        return false;

                    var readyCondition = r.getStatus().getConditions().stream().filter(condition -> condition.getType() == Conditions.Type.READY).findFirst();
                    return readyCondition.isPresent() && readyCondition.get().getStatus() == Conditions.Status.TRUE;
                }, K8S_RESOURCE_TIMEOUT, TimeUnit.MINUTES);


        // Assert that existing DB entries are loaded into the Cache
        var gingersnap = GingersnapClient.of(cache);
        var keys = gingersnap.getAllKeys(ruleName).collect(Collectors.toList());
        assertThat(keys, hasSize(1));
        assertThat(keys.get(0), is(not("[]")));
        assertThat(
                gingersnap.get(ruleName, "1", Customer.class),
                equalTo(new Customer("Alice", "alice@example.com"))
        );

        // Delete entry from DB and assert entry also removed from Cache
        db.delete(new Customer(1L, "Alice", "alice@example.com"));
        assertThat(
                db.query("FROM customer c WHERE c.id = ?1", Customer.class)
                        .setParameter(1, 1L)
                        .getResultList(),
                is(empty())
        );
        // TODO update to null when 404 returned?
        eventually(() -> gingersnap.get(ruleName, "1").isEmpty());

        // Add entry to the DB and wait for it to appear in the Cache
        Customer customer = new Customer("Ryan", "ryan@example.com");
        db.insert(customer);

        Customer dbCustomer = db.query("FROM customer c WHERE c.fullname = ?1 AND c.email = ?2", Customer.class)
                .setParameter(1, customer.getFullname())
                .setParameter(2, customer.getEmail())
                .getSingleResult();
        assertThat(dbCustomer, notNullValue());

        var key = Long.toString(dbCustomer.getId());
        // TODO update to null when 404 returned?
        eventually(() -> !"".equals(gingersnap.get(ruleName, key)));

        var c = gingersnap.get(ruleName, key, Customer.class);
        assertThat(c.getFullname(), is(customer.getFullname()));
        assertThat(c.getEmail(), is(customer.getEmail()));
    }
}
