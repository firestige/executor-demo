package xyz.firestige.redis.ack.api;

/**
 * HTTP 客户端抽象接口
 *
 * <p>用于隔离 ack-core 对具体 HTTP 客户端实现的依赖
 *
 * @author AI
 * @since 1.0
 */
public interface HttpClient {

    /**
     * 执行 HTTP GET 请求
     *
     * @param url 请求 URL
     * @return HTTP 响应
     * @throws HttpClientException 请求失败时抛出
     */
    HttpResponse get(String url) throws HttpClientException;

    /**
     * 执行 HTTP POST 请求
     *
     * @param url 请求 URL
     * @param body 请求体（JSON 格式）
     * @return HTTP 响应
     * @throws HttpClientException 请求失败时抛出
     */
    HttpResponse post(String url, String body) throws HttpClientException;
}

