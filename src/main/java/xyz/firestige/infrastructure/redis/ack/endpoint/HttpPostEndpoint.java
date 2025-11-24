package xyz.firestige.infrastructure.redis.ack.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.infrastructure.redis.ack.api.AckContext;
import xyz.firestige.infrastructure.redis.ack.api.AckEndpoint;
import xyz.firestige.infrastructure.redis.ack.exception.AckEndpointException;

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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpPostEndpoint(String url, Function<String, Object> bodyBuilder,
                           RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.url = url;
        this.bodyBuilder = bodyBuilder;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String query(AckContext context) throws AckEndpointException {
        try {
            // 构建请求体
            Object requestBody = bodyBuilder.apply(context.getFootprint());

            // 设置 Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Object> request = new HttpEntity<>(requestBody, headers);

            // 发送 POST 请求
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AckEndpointException("HTTP POST failed with status: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (Exception e) {
            throw new AckEndpointException("HTTP POST request failed: " + url, e);
        }
    }
}

