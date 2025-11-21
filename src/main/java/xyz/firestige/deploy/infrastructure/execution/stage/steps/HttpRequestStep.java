package xyz.firestige.deploy.infrastructure.execution.stage.steps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import xyz.firestige.deploy.domain.task.TaskRuntimeContext;
import xyz.firestige.deploy.infrastructure.execution.stage.StageStep;
import xyz.firestige.deploy.infrastructure.execution.stage.http.HttpRequestData;
import xyz.firestige.deploy.infrastructure.execution.stage.http.HttpResponseData;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求 Step（通用，数据无关）
 *
 * <p>职责：
 * <ul>
 *   <li>从 TaskRuntimeContext 提取 HTTP 请求数据</li>
 *   <li>发送 HTTP 请求</li>
 *   <li>将响应数据放回 TaskRuntimeContext</li>
 * </ul>
 *
 * <p>不做业务判断，只负责技术实现
 *
 * <p>数据约定：
 * <ul>
 *   <li>输入：TaskRuntimeContext 中的 "url", "method", "headers", "body"</li>
 *   <li>输出：TaskRuntimeContext 中的 "httpResponse"（HttpResponseData）</li>
 * </ul>
 *
 * @since RF-19 三层抽象架构
 */
public class HttpRequestStep implements StageStep {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestStep.class);

    private final RestTemplate restTemplate;

    public HttpRequestStep(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getStepName() {
        return "http-request";
    }

    @Override
    public void execute(TaskRuntimeContext ctx) throws Exception {
        // 1. 准备数据（从 TaskRuntimeContext 提取）
        HttpRequestData requestData = prepareData(ctx);

        // 2. 执行动作（发送 HTTP 请求）
        long startTime = System.currentTimeMillis();
        HttpResponseData responseData = executeAction(requestData);
        responseData.setDurationMs(System.currentTimeMillis() - startTime);

        // 3. 返回结果（放入 TaskRuntimeContext）
        ctx.addVariable("httpResponse", responseData);

        log.info("HTTP {} {} → {} (耗时 {}ms)",
            requestData.getMethod(),
            requestData.getUrl(),
            responseData.getStatusCode(),
            responseData.getDurationMs());
    }

    /**
     * 准备数据（从 TaskRuntimeContext 提取）
     */
    private HttpRequestData prepareData(TaskRuntimeContext ctx) {
        String url = (String) ctx.getAdditionalData("url");
        String method = (String) ctx.getAdditionalData("method");

        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url is required in TaskRuntimeContext");
        }
        if (method == null || method.isEmpty()) {
            throw new IllegalArgumentException("method is required in TaskRuntimeContext");
        }

        HttpRequestData.Builder builder = HttpRequestData.builder()
            .url(url)
            .method(method);

        // 可选的 headers
        @SuppressWarnings("unchecked")
        Map<String, String> headers = ctx.getAdditionalData("headers", Map.class);
        if (headers != null) {
            builder.headers(headers);
        }

        // 可选的 body
        Object body = ctx.getAdditionalData("body");
        if (body != null) {
            builder.body(body);
        }

        return builder.build();
    }

    /**
     * 执行动作（发送 HTTP 请求）
     */
    private HttpResponseData executeAction(HttpRequestData requestData) {
        HttpResponseData.Builder responseBuilder = HttpResponseData.builder();

        try {
            // 构建 Spring HttpHeaders
            HttpHeaders headers = new HttpHeaders();
            if (requestData.getHeaders() != null) {
                requestData.getHeaders().forEach(headers::set);
            }

            // 构建 HttpEntity
            HttpEntity<Object> entity = new HttpEntity<>(requestData.getBody(), headers);

            // 发送请求
            ResponseEntity<String> responseEntity;
            String method = requestData.getMethod().toUpperCase();

            switch (method) {
                case "GET":
                    responseEntity = restTemplate.getForEntity(requestData.getUrl(), String.class);
                    break;

                case "POST":
                    responseEntity = restTemplate.postForEntity(requestData.getUrl(), entity, String.class);
                    break;

                case "PUT":
                    responseEntity = restTemplate.exchange(
                        requestData.getUrl(),
                        HttpMethod.PUT,
                        entity,
                        String.class
                    );
                    break;

                case "DELETE":
                    responseEntity = restTemplate.exchange(
                        requestData.getUrl(),
                        HttpMethod.DELETE,
                        entity,
                        String.class
                    );
                    break;

                default:
                    throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
            }

            // 填充响应
            responseBuilder.statusCode(responseEntity.getStatusCodeValue());
            responseBuilder.body(responseEntity.getBody());

            // 提取响应 headers
            if (responseEntity.getHeaders() != null) {
                Map<String, String> responseHeaders = new HashMap<>();
                responseEntity.getHeaders().forEach((key, values) -> {
                    if (values != null && !values.isEmpty()) {
                        responseHeaders.put(key, values.get(0));
                    }
                });
                responseBuilder.headers(responseHeaders);
            }

        } catch (Exception e) {
            log.error("HTTP 请求失败: {} {}", requestData.getMethod(), requestData.getUrl(), e);
            responseBuilder.exception(e);
            responseBuilder.statusCode(0);  // 异常时状态码为 0
        }

        return responseBuilder.build();
    }
}

