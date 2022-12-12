package io.gingersnapproject;

import io.gingersnap_project.v1alpha1.cachespec.DataSource;
import io.gingersnap_project.v1alpha1.lazycacherulestatus.Conditions;
import io.gingersnapproject.data.Customer;
import io.gingersnapproject.gingersnap.GingersnapClient;
import io.gingersnapproject.kubernetes.KubernetesClientResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.gingersnapproject.kubernetes.Util.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(KubernetesClientResolver.class)
abstract class AbstractLazyCacheTest extends AbstractTest {

    protected final DataSource.DbType dsType;
    protected final String sqlPlaceholder;

    public AbstractLazyCacheTest(DataSource.DbType dsType, String sqlPlaceholder) {
        this.dsType = dsType;
        this.sqlPlaceholder = sqlPlaceholder;
    }

    protected String sql(String query) {
        return query.replace("?", sqlPlaceholder);
    }

    @Test
    public void test() {
        String cacheName = "lazy-cache";
        String connSecretName = "db-credential-secret";
        k8s.resource(db.connectionSecret(connSecretName))
                .inNamespace(namespace)
                .create();

        // Create Cache
        var cache = cache(dsType, cacheName, connSecretName);
        cache = createAndWaitForCache(cache);

        // Create Lazy Rule
        var ruleName = "e2e-rule";
        var rule = k8s.resource(
                        lazyCacheRule(
                                ruleName,
                                cache,
                                sql("SELECT fullname, email FROM customer WHERE id = ?")
                        )
                )
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


        // Assert that entry is loaded into the Cache
        var gingersnap = GingersnapClient.of(cache);
        var keys = gingersnap.getAllKeys(ruleName).collect(Collectors.toList());
        assertThat(keys, hasSize(1));
        assertThat(keys.get(0), is("[]"));
        assertThat(
                gingersnap.get(ruleName, "1", Customer.class),
                equalTo(new Customer("Alice", "alice@example.com"))
        );

        // Delete from DB and assert that value still exists in Cache
        db.delete(new Customer(1L, "Alice", "alice@example.com"));
        sleep(1000);
        assertThat(
                gingersnap.get(ruleName, "1", Customer.class),
                equalTo(new Customer("Alice", "alice@example.com"))
        );
    }
}
