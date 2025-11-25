/**
 * Footprint 提取器实现
 * <p>
 * 提供多种预置的 Footprint 提取策略：
 * <ul>
 *   <li>{@link xyz.firestige.redis.ack.extractor.JsonFieldExtractor} - 从 JSON 对象提取指定字段</li>
 *   <li>{@link xyz.firestige.redis.ack.extractor.RegexFootprintExtractor} - 使用正则表达式提取</li>
 *   <li>{@link xyz.firestige.redis.ack.extractor.FunctionFootprintExtractor} - 自定义函数提取</li>
 * </ul>
 *
 * @author AI
 * @since 1.0
 */
package xyz.firestige.redis.ack.extractor;

