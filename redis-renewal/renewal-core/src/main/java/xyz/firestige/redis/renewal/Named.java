package xyz.firestige.redis.renewal;

/**
 * 命名接口，提供获取名称的方法。
 */
public interface Named {
    /**
     * 获取名称
     *
     * @return 策略的名字
     */
    default String getName(){
        return this.getClass().getSimpleName();
    }
}
