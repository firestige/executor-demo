package xyz.firestige.redis.ack.api;

/**
 * HTTP 响应封装
 *
 * @author AI
 * @since 1.0
 */
public class HttpResponse {

    private final int statusCode;
    private final String body;

    public HttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    /**
     * 获取 HTTP 状态码
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 获取响应体
     */
    public String getBody() {
        return body;
    }

    /**
     * 判断是否为成功响应（2xx）
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public String toString() {
        return "HttpResponse{statusCode=" + statusCode + ", bodyLength=" +
               (body != null ? body.length() : 0) + "}";
    }
}

