package xyz.firestige.executor.domain.shared.vo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * NetworkEndpoint 值对象
 *
 * DDD 重构：封装网络端点（URL）
 *
 * 职责：
 * 1. 封装 URL 格式验证
 * 2. 提供类型安全
 * 3. 提供便捷的 URL 操作方法
 * 4. 不可变对象，线程安全
 */
public final class NetworkEndpoint {

    private final String url;

    /**
     * 私有构造函数
     *
     * @param url URL 字符串
     */
    private NetworkEndpoint(String url) {
        this.url = url;
    }

    /**
     * 创建 NetworkEndpoint（带验证）
     *
     * @param url URL 字符串
     * @return NetworkEndpoint 实例
     * @throws IllegalArgumentException 如果 URL 格式无效
     */
    public static NetworkEndpoint of(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Network Endpoint URL 不能为空");
        }

        // 验证 URL 格式
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                String.format("Network Endpoint URL 格式无效: %s", url), e
            );
        }

        return new NetworkEndpoint(url);
    }

    /**
     * 创建 NetworkEndpoint（不验证，用于已知合法的场景）
     *
     * @param url URL 字符串
     * @return NetworkEndpoint 实例
     */
    public static NetworkEndpoint ofTrusted(String url) {
        return new NetworkEndpoint(url);
    }

    /**
     * 获取原始 URL
     *
     * @return URL 字符串
     */
    public String getUrl() {
        return url;
    }

    /**
     * 判断是否是 HTTPS 端点
     *
     * @return 如果是 HTTPS 则返回 true
     */
    public boolean isSecure() {
        return url.startsWith("https://");
    }

    /**
     * 判断是否是 HTTP 端点
     *
     * @return 如果是 HTTP 则返回 true
     */
    public boolean isHttp() {
        return url.startsWith("http://");
    }

    /**
     * 获取主机名
     *
     * @return 主机名，如果解析失败则返回 null
     */
    public String getHost() {
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * 获取端口号
     *
     * @return 端口号，如果未指定则返回 -1
     */
    public int getPort() {
        try {
            return new URL(url).getPort();
        } catch (MalformedURLException e) {
            return -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkEndpoint that = (NetworkEndpoint) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return url;
    }
}

