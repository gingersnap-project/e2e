package io.gingersnapproject.database;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.gingersnapproject.data.Customer;
import io.gingersnapproject.kubernetes.KubernetesClientResolver;
import io.gingersnapproject.kubernetes.Util;
import jakarta.persistence.*;
import org.junit.jupiter.api.extension.*;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.gingersnapproject.kubernetes.Util.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

abstract class AbstractDatabase implements AfterAllCallback, AfterEachCallback, BeforeAllCallback,
        BeforeEachCallback, Database, ParameterResolver {

    final KubernetesClient k8s = KubernetesClientResolver.resolve();

    final String namespace;
    final String vendor;
    final int port;

    Closeable forwardedPort;
    EntityManagerFactory emf;
    EntityManager em;

    public AbstractDatabase(String vendor, int port) {
        this.namespace = vendor;
        this.port = port;
        this.vendor = vendor;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        waitForNamespaceDeletion(k8s, namespace);
        createNamespace(k8s, namespace);

        var resources = k8s
                .load(resourceStream(vendor + ".yaml"))
                .inNamespace(namespace)
                .create();

        k8s.resourceList(resources).waitUntilReady(4, TimeUnit.MINUTES);
        forwardedPort = forwardPort(k8s, namespace, vendor, port, port);

        var initializer = initializer();
        if (initializer == null)
            return;

        // Create ConfigMap containing init scripts
        var data = initializer.scripts.stream().collect(Collectors.toMap(Function.identity(), this::resource));
        var configMap = k8s.resource(
                        new ConfigMapBuilder()
                                .withNewMetadata()
                                .withName("sql-setup")
                                .withNamespace(namespace)
                                .endMetadata()
                                .withData(data)
                                .build()
                ).inNamespace(namespace)
                .create();

        // Execute initialization job
        var job = new JobBuilder()
                .withNewMetadata()
                .withName("sql-initializer")
                .withNamespace(namespace)
                .endMetadata()
                .withSpec(
                        new JobSpecBuilder()
                                .withTemplate(
                                        new PodTemplateSpecBuilder()
                                                .withSpec(
                                                        new PodSpecBuilder()
                                                                .withContainers(
                                                                        new ContainerBuilder()
                                                                                .withName("initializer")
                                                                                .withImage(initializer.image)
                                                                                .withCommand(initializer.commands)
                                                                                .withVolumeMounts(
                                                                                        new VolumeMountBuilder()
                                                                                                .withName("initdb")
                                                                                                .withMountPath("/init")
                                                                                                .build()
                                                                                )
                                                                                .build()
                                                                )
                                                                .withRestartPolicy("OnFailure")
                                                                .withVolumes(
                                                                        new VolumeBuilder()
                                                                                .withName("initdb")
                                                                                .withConfigMap(
                                                                                        new ConfigMapVolumeSourceBuilder()
                                                                                                .withName(configMap.getMetadata().getName())
                                                                                                .withDefaultMode(0777)
                                                                                                .build()
                                                                                )
                                                                                .build()
                                                                )
                                                                .build()
                                                ).build()
                                ).build()
                ).build();

        job = k8s.resource(job)
                .inNamespace(namespace)
                .create();

        // Get All pods created by the job and wait for pod to complete
        var pods = k8s.pods().inNamespace(namespace).withLabel("job-name", job.getMetadata().getName());
        eventually(() -> pods.list().getItems().size() > 0);
        PodList podList = pods.list();
        k8s.pods()
                .inNamespace(namespace)
                .withName(podList.getItems().get(0).getMetadata().getName())
                .waitUntilCondition(pod -> pod.getStatus().getPhase().equals("Succeeded"), 4, TimeUnit.MINUTES);

        emf = Persistence.createEntityManagerFactory(String.format("io.gingersnapproject.%s.%s", vendor, Util.LOCAL_TEST_EXECUTION ? "local" : "deployed"));
        em = emf.createEntityManager();
    }

    public record DBInitializer(String image, List<String> commands, Set<String> scripts) {
    }

    protected DBInitializer initializer() {
        return null;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        // Reset tables
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.createQuery("DELETE FROM customer").executeUpdate();
        tx.commit();

        // Populate tables
        insert(new Customer("Alice", "alice@example.com"));

        // Ensure that expected number of entries have been added to the table(s)
        assertThat(
                query("FROM customer c WHERE c.id = ?1", Customer.class)
                        .setParameter(1, 1L)
                        .getResultList(),
                hasSize(1)
        );
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (forwardedPort != null) forwardedPort.close();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (em != null) em.close();
        if (emf != null) emf.close();
        k8s.namespaces().withName(namespace).delete();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType()
                .equals(Database.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return this;
    }

    protected void connectionProperties(Map<String, String> props) {
    }

    @Override
    public Secret connectionSecret(String name) {
        var props = new HashMap<>(
                Map.of(
                        "type", vendor,
                        "host", host(),
                        "port", Integer.toString(port),
                        "database", "gingersnap",
                        "username", "gingersnap_user",
                        "password", "Password!42"
                )
        );
        connectionProperties(props);
        return new SecretBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withStringData(props)
                .build();
    }

    protected String host() {
        return String.format("%s.%s.svc.cluster.local", vendor, namespace);
    }

    @Override
    public void insert(Object entity) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.persist(entity);
        tx.commit();
    }

    @Override
    public void delete(Object entity) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        em.remove(em.merge(entity));
        tx.commit();
    }

    @Override
    public <T> TypedQuery<T> query(String query, Class<T> resultClass) {
        return em.createQuery(query, resultClass);
    }

    protected InputStream resourceStream(String name) {
        return AbstractDatabase.class.getResourceAsStream(String.format("/kubernetes/database/%s/%s", vendor, name));
    }

    protected String resource(String name) {
        try {
            var resource = String.format("/kubernetes/database/%s/%s", vendor, name);
            var path = Paths.get(AbstractDatabase.class.getResource(resource).toURI());
            return Files.readString(path);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
