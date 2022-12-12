package io.gingersnapproject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.gingersnap_project.v1alpha1.Cache;
import io.gingersnap_project.v1alpha1.cachestatus.Conditions;
import io.gingersnapproject.database.Database;
import io.gingersnapproject.kubernetes.KubernetesClientResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

import static io.gingersnapproject.kubernetes.Util.*;
@ExtendWith(KubernetesClientResolver.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractTest {
    public static final int K8S_RESOURCE_TIMEOUT = 4;
    protected final String namespace = "test";

    protected Database db;
    protected KubernetesClient k8s;

    Closeable forwardedPort;

    @BeforeAll
    public void beforeAll(Database database, KubernetesClient k8sClient) {
        this.db = database;
        this.k8s = k8sClient;
    }

    @BeforeEach
    public void beforeEach() {
        waitForNamespaceDeletion(k8s, namespace);
        createNamespace(k8s, namespace);
    }

    @AfterEach
    public void afterEach() throws Exception {
        if (forwardedPort != null) forwardedPort.close();
        k8s.namespaces().withName(namespace).delete();
    }

    protected Cache createAndWaitForCache(Cache cache) {
        cache = k8s.resource(cache)
                .inNamespace(namespace)
                .create();

        // Wait for Cache to become Ready
        cache = k8s.resource(cache)
                .inNamespace(namespace)
                .waitUntilCondition(c -> {
                    if (c.getStatus() == null || c.getStatus().getConditions() == null)
                        return false;

                    var readyCondition = c.getStatus().getConditions().stream().filter(condition -> condition.getType() == Conditions.Type.READY).findFirst();
                    return readyCondition.isPresent() && readyCondition.get().getStatus() == Conditions.Status.TRUE;
                }, K8S_RESOURCE_TIMEOUT, TimeUnit.MINUTES);

        forwardedPort = forwardPort(k8s, namespace, "infinispan", 8080, 8080);
        return cache;
    }
}
