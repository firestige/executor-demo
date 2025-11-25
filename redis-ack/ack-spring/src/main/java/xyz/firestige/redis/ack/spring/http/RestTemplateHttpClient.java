package xyz.firestige.redis.ack.spring.http;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.redis.ack.api.HttpClient;
import xyz.firestige.redis.ack.api.HttpClientException;
import xyz.firestige.redis.ack.api.HttpResponse;

/**
 * 基于 Spring RestTemplate 的 HttpClient 实现
 *
 * @author AI
 * @since 1.0
 */
public class RestTemplateHttpClient implements HttpClient {

    private final RestTemplate restTemplate;

    public RestTemplateHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public HttpResponse get(String url) throws HttpClientException {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return new HttpResponse(
                response.getStatusCode().value(),
                response.getBody()
            );
        } catch (Exception e) {
            throw new HttpClientException("HTTP GET request failed: " + url, e);
        }
    }

    @Override
    public HttpResponse post(String url, String body) throws HttpClientException {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);
            return new HttpResponse(
                response.getStatusCode().value(),
                response.getBody()
            );
        } catch (Exception e) {
            throw new HttpClientException("HTTP POST request failed: " + url, e);
        }
    }
}

