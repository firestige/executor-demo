package xyz.firestige.deploy.infrastructure.execution.stage.http;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求数据
 * 用于 HttpRequestStep 的输入数据
 *
 * @since RF-19 三层抽象架构
 */
public class HttpRequestData {

    private String url;
    private String method;  // GET, POST, PUT, DELETE
    private Map<String, String> headers;
    private Object body;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;

    private HttpRequestData() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final HttpRequestData data = new HttpRequestData();

        public Builder url(String url) {
            data.url = url;
            return this;
        }

        public Builder method(String method) {
            data.method = method;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            data.headers = headers;
            return this;
        }

        public Builder addHeader(String key, String value) {
            if (data.headers == null) {
                data.headers = new HashMap<>();
            }
            data.headers.put(key, value);
            return this;
        }

        public Builder body(Object body) {
            data.body = body;
            return this;
        }

        public Builder connectTimeoutMs(Integer connectTimeoutMs) {
            data.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder readTimeoutMs(Integer readTimeoutMs) {
            data.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public HttpRequestData build() {
            if (data.url == null || data.url.isEmpty()) {
                throw new IllegalArgumentException("url is required");
            }
            if (data.method == null || data.method.isEmpty()) {
                throw new IllegalArgumentException("method is required");
            }
            return data;
        }
    }

    // Getters

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Object getBody() {
        return body;
    }

    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }
}

