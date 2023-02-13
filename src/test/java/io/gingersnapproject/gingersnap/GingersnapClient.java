package io.gingersnapproject.gingersnap;

import com.google.gson.Gson;
import io.gingersnap_project.v1alpha1.Cache;
import io.gingersnapproject.kubernetes.Util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.core.Is.is;

public class GingersnapClient {

    public static GingersnapClient of(Cache cache){
        URI uri;
        if (Util.LOCAL_TEST_EXECUTION) {
            uri = URI.create("http://localhost:8080");
        } else {
            var meta = cache.getMetadata();
            uri = URI.create(String.format("http://%s.%s.svc.cluster.local", meta.getName(), meta.getNamespace()));
        }
        return new GingersnapClient(uri);
    }

    final HttpClient client;
    final URI uri;

    private GingersnapClient(URI uri) {
        this.client = HttpClient.newHttpClient();
        this.uri = uri;
    }

    public String get(String rule, String key) {
        var path = String.format("/rules/%s/%s", rule, key);
        var req = HttpRequest.newBuilder()
                .uri(uri.resolve(path))
                .build();
        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertThat(rsp.statusCode(), anyOf(is(200), is(404)));
            return rsp.body();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T get(String rule, String key, Class<T> entity) {
        var json = get(rule, key);
        return fromJson(json, entity);
    }

    public Stream<String> getAllKeys(String rule) {
        var path = String.format("/rules/%s", rule);
        var req = HttpRequest.newBuilder()
                .uri(uri.resolve(path))
                .build();
        try {
            var rsp = client.send(req, HttpResponse.BodyHandlers.ofLines());
            assertThat(rsp.statusCode(), anyOf(is(200), is(404)));
            return rsp.body();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T fromJson(String json, Class<T> entity) {
        if (json == null)
            return null;

        return new Gson()
                .fromJson(json, entity);
    }
}
