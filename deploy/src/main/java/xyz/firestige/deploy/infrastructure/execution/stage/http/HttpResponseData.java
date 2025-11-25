package xyz.firestige.deploy.infrastructure.execution.stage.http;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 响应数据
 * 用于 HttpRequestStep 的输出数据
 *
 * @since RF-19 三层抽象架构
 */
public class HttpResponseData {

    private int statusCode;
    private Map<String, String> headers;
    private String body;
    private Long durationMs;
    private Exception exception;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private HttpResponseData() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final HttpResponseData data = new HttpResponseData();

        public Builder statusCode(int statusCode) {
            data.statusCode = statusCode;
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

        public Builder body(String body) {
            data.body = body;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            data.durationMs = durationMs;
            return this;
        }

        public Builder exception(Exception exception) {
            data.exception = exception;
            return this;
        }

        public HttpResponseData build() {
            return data;
        }
    }

    /**
     * 检查是否是 2xx 状态码
     */
    public boolean is2xx() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 检查是否成功（2xx 且无异常）
     */
    public boolean isSuccess() {
        return is2xx() && exception == null;
    }

    /**
     * 解析响应 Body 为指定类型
     *
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 解析后的对象
     * @throws Exception 解析失败
     */
    public <T> T parseBody(Class<T> clazz) throws Exception {
        if (body == null || body.isEmpty()) {
            return null;
        }
        return objectMapper.readValue(body, clazz);
    }

    // Getters and Setters

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}

