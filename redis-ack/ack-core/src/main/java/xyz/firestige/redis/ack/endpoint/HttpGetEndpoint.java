package xyz.firestige.redis.ack.endpoint;

import xyz.firestige.redis.ack.api.AckContext;
import xyz.firestige.redis.ack.api.AckEndpoint;
import xyz.firestige.redis.ack.api.HttpClient;
import xyz.firestige.redis.ack.api.HttpResponse;
import xyz.firestige.redis.ack.exception.AckEndpointException;

/**
 * HTTP GET 端点实现
 *
 * @author AI
 * @since 1.0
 */
public class HttpGetEndpoint implements AckEndpoint {

    private final String url;
    private final HttpClient httpClient;

    public HttpGetEndpoint(String url, HttpClient httpClient) {
        this.url = url;
        this.httpClient = httpClient;
    }

    @Override
    public String query(AckContext context) throws AckEndpointException {
        try {
            HttpResponse response = httpClient.get(url);

            if (!response.isSuccess()) {
                throw new AckEndpointException("HTTP GET failed with status: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (Exception e) {
            throw new AckEndpointException("HTTP GET request failed: " + url, e);
        }
    }
}

