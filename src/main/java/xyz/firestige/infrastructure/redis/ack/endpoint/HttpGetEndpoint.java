package xyz.firestige.infrastructure.redis.ack.endpoint;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.infrastructure.redis.ack.api.AckContext;
import xyz.firestige.infrastructure.redis.ack.api.AckEndpoint;
import xyz.firestige.infrastructure.redis.ack.exception.AckEndpointException;

/**
 * HTTP GET 端点实现
 *
 * @author AI
 * @since 1.0
 */
public class HttpGetEndpoint implements AckEndpoint {

    private final String url;
    private final RestTemplate restTemplate;

    public HttpGetEndpoint(String url, RestTemplate restTemplate) {
        this.url = url;
        this.restTemplate = restTemplate;
    }

    @Override
    public String query(AckContext context) throws AckEndpointException {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AckEndpointException("HTTP GET failed with status: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (Exception e) {
            throw new AckEndpointException("HTTP GET request failed: " + url, e);
        }
    }
}

