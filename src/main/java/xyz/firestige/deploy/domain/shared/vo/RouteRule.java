package xyz.firestige.deploy.domain.shared.vo;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Objects;

/**
 * 路由规则值对象
 */
public record RouteRule(String id, URI sourceUri, URI targetUri) {
    public static RouteRule of(String id,
                               String sourceUri, String sourceDomain,
                               String targetUri, String tagetDomain){
        Objects.requireNonNull(id, "id cannot be null");
        URI source = Objects.requireNonNull(resolve(sourceUri, sourceDomain), "Source URI cannot be null");
        URI target = Objects.requireNonNull(resolve(targetUri, tagetDomain), "Target URI cannot be null");
        return new RouteRule(id, source, target);
    }

    private static URI resolve(String a, String b) {
        if (!StringUtils.hasText(a) && !StringUtils.hasText(b)) {
            return null;
        }
        int lenA= Objects.isNull(a) ? 0 : a.length();
        int lenB = Objects.isNull(b) ? 0 : b.length();
        return lenA > lenB ? URI.create(a) : URI.create(b);
    }
}

