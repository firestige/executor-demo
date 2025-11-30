package xyz.firestige.deploy.domain.shared.vo;

/**
 * 路由规则值对象
 *
 * @param id       路由规则ID
 * @param targets 1个或多个目标地址，逗号分隔
 */
public record RouteRule(String id, String targets) {}

