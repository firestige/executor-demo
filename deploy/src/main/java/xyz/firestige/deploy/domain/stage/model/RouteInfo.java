package xyz.firestige.deploy.domain.stage.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 路由信息（用于 Redis 序列化）
 *
 * 对应 JSON 结构：
 * {
 *   "id": "route_001",
 *   "sourceUri": "uri1",
 *   "targetUri": "uri2"
 * }
 */
public class RouteInfo {

    @JsonProperty("routeId")
    private String id;

    @JsonProperty("sourceUri")
    private String sourceUri;

    @JsonProperty("targetUri")
    private String targetUri;

    public RouteInfo() {
    }

    public RouteInfo(String id, String sourceUri, String targetUri) {
        this.id = id;
        this.sourceUri = sourceUri;
        this.targetUri = targetUri;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getTargetUri() {
        return targetUri;
    }

    public void setTargetUri(String targetUri) {
        this.targetUri = targetUri;
    }

    @Override
    public String toString() {
        return "RouteInfo{" +
                "id='" + id + '\'' +
                ", sourceUri='" + sourceUri + '\'' +
                ", targetUri='" + targetUri + '\'' +
                '}';
    }
}

