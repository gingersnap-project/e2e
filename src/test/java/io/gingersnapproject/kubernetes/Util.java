package io.gingersnapproject.kubernetes;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Gettable;
import io.gingersnap_project.v1alpha1.*;
import io.gingersnap_project.v1alpha1.cachespec.DataSource;
import io.gingersnap_project.v1alpha1.cachespec.datasource.SecretRef;
import io.gingersnap_project.v1alpha1.eagercacherulespec.Key;
import io.gingersnap_project.v1alpha1.eagercacherulespec.Value;
import io.gingersnap_project.v1alpha1.lazycacherulespec.CacheRef;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

public class Util {

    public static final boolean LOCAL_TEST_EXECUTION = System.getenv("KUBERNETES_SERVICE_HOST") == null;

    private Util() {
        System.out.println("LOCAL_TEST_EXECUTION=%s" + LOCAL_TEST_EXECUTION);
    }

    public static void waitForNamespaceDeletion(KubernetesClient k8s, String namespace) {
        k8s.namespaces().withName(namespace).waitUntilCondition(Objects::isNull, 4, TimeUnit.MINUTES);
    }

    public static Closeable forwardPort(KubernetesClient k8s, String namespace, String name, int containerPort, int localPort) {
        if (LOCAL_TEST_EXECUTION) {
            var pods = k8s.pods()
                    .inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", name)
                    .resources()
                    .map(Gettable::get)
                    .filter(p -> {
                        return p.getStatus().getContainerStatuses().stream().allMatch(ContainerStatus::getReady);
                    })
                    .collect(Collectors.toList());

            assertThat(
                    pods.stream().map(p -> p.getMetadata().getName()).collect(Collectors.joining(",")),
                    pods,
                    hasSize(1)
            );
            var podName = pods.get(0).getMetadata().getName();
            System.out.printf("Forwarding port %d:%d for pod '%s'\n", containerPort, localPort, pods.get(0).getMetadata().getName());
            return k8s.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .portForward(containerPort, localPort);
        }
        return () -> {};
    }

    public static void createNamespace(KubernetesClient k8s, String namespace) {
        k8s.namespaces()
                .resource(
                        new NamespaceBuilder()
                                .withNewMetadata()
                                .withName(namespace)
                                .endMetadata()
                                .build()
                ).create();
    }

    public static Cache cache(DataSource.DbType dbType, String name, String secret) {
        var objectMeta = new ObjectMeta();
        var secretRef = new SecretRef();
        var datasource = new DataSource();
        var cacheSpec = new CacheSpec();
        var cache = new Cache();

        objectMeta.setName(name);

        secretRef.setName(secret);

        datasource.setDbType(dbType);
        datasource.setSecretRef(secretRef);

        cacheSpec.setDataSource(datasource);

        cache.setSpec(cacheSpec);
        cache.setMetadata(objectMeta);
        return cache;
    }

    public static LazyCacheRule lazyCacheRule(String name, Cache cache, String query) {
        var objectMeta = new ObjectMeta();
        var rule = new LazyCacheRule();
        var spec = new LazyCacheRuleSpec();
        var cacheRef = new CacheRef();

        objectMeta.setName(name);

        cacheRef.setName(cache.getMetadata().getName());
        cacheRef.setNamespace(cache.getMetadata().getNamespace());

        spec.setCacheRef(cacheRef);
        spec.setQuery(query);

        rule.setMetadata(objectMeta);
        rule.setSpec(spec);
        return rule;
    }

    public static EagerCacheRule eagerCacheRule(String name, Cache cache, String table, Consumer<Key> keyConsumer,
                                                Consumer<Value> valueConsumer) {
        var objectMeta = new ObjectMeta();
        var rule = new EagerCacheRule();
        var spec = new EagerCacheRuleSpec();
        var cacheRef = new io.gingersnap_project.v1alpha1.eagercacherulespec.CacheRef();
        var key = new Key();
        var value = new Value();

        assertThat(keyConsumer, notNullValue());
        keyConsumer.accept(key);

        if (valueConsumer != null)
            valueConsumer.accept(value);

        objectMeta.setName(name);

        cacheRef.setName(cache.getMetadata().getName());
        cacheRef.setNamespace(cache.getMetadata().getNamespace());

        spec.setCacheRef(cacheRef);
        spec.setKey(key);
        spec.setValue(value);
        spec.setTableName(table);

        rule.setMetadata(objectMeta);
        rule.setSpec(spec);
        return rule;
    }


    public static void eventually(Supplier<Boolean> condition) {
        eventually(condition, 1, TimeUnit.MINUTES);
    }

    public static void eventually(Supplier<Boolean> condition, long timeout, TimeUnit unit) {
        eventually(() -> "Condition is still false after " + timeout + " " + unit, condition, timeout, unit);
    }

    public static void eventually(Supplier<String> messageSupplier, Supplier<Boolean> condition, long timeout,
                                  TimeUnit timeUnit) {
        try {
            long timeoutNanos = timeUnit.toNanos(timeout);
            // We want the sleep time to increase in arithmetic progression
            // 30 loops with the default timeout of 30 seconds means the initial wait is ~ 65 millis
            int loops = 30;
            int progressionSum = loops * (loops + 1) / 2;
            long initialSleepNanos = timeoutNanos / progressionSum;
            long sleepNanos = initialSleepNanos;
            long expectedEndTime = System.nanoTime() + timeoutNanos;
            while (expectedEndTime - System.nanoTime() > 0) {
                if (condition.get())
                    return;
                LockSupport.parkNanos(sleepNanos);
                sleepNanos += initialSleepNanos;
            }
            if (!condition.get()) {
                fail(messageSupplier.get());
            }
        } catch (Exception e) {
            throw new RuntimeException("Unexpected!", e);
        }
    }

    public static void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
