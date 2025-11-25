package xyz.firestige.redis.ack.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.firestige.redis.ack.api.AckContext;
import xyz.firestige.redis.ack.api.AckEndpoint;
import xyz.firestige.redis.ack.api.HttpClient;
import xyz.firestige.redis.ack.api.HttpResponse;
import xyz.firestige.redis.ack.exception.AckEndpointException;

import java.util.function.Function;

/**
 * HTTP POST 端点实现
 *
 * @author AI
 * @since 1.0
 */
public class HttpPostEndpoint implements AckEndpoint {

    private final String url;
    private final Function<String, Object> bodyBuilder;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpPostEndpoint(String url, Function<String, Object> bodyBuilder,
                           HttpClient httpClient, ObjectMapper objectMapper) {
        this.url = url;
        this.bodyBuilder = bodyBuilder;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String query(AckContext context) throws AckEndpointException {
        try {
            // 构建请求体
            Object requestBody = bodyBuilder.apply(context.getFootprint());

            // 序列化为 JSON
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 发送 POST 请求
            HttpResponse response = httpClient.post(url, jsonBody);

            if (!response.isSuccess()) {
                throw new AckEndpointException("HTTP POST failed with status: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (Exception e) {
            throw new AckEndpointException("HTTP POST request failed: " + url, e);
        }
    }
}

