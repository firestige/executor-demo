package xyz.firestige.deploy.config.stage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StageConfigurable 接口测试
 */
class StageConfigurableTest {

    /**
     * 测试用的配置类
     */
    static class TestStageConfig implements StageConfigurable {
        private boolean enabled;

        public TestStageConfig(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }
    }

    /**
     * 自定义名称的配置类
     */
    static class CustomNameStageConfig implements StageConfigurable {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getStageName() {
            return "自定义阶段";
        }
    }

    @Test
    void shouldImplementIsEnabled() {
        TestStageConfig enabled = new TestStageConfig(true);
        TestStageConfig disabled = new TestStageConfig(false);

        assertThat(enabled.isEnabled()).isTrue();
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void shouldHaveDefaultGetStageName() {
        TestStageConfig config = new TestStageConfig(true);

        // 类名是 TestStageConfig，应该移除 "StageConfig" 后缀
        assertThat(config.getStageName()).isEqualTo("Test");
    }

    @Test
    void shouldAllowCustomGetStageName() {
        CustomNameStageConfig config = new CustomNameStageConfig();

        assertThat(config.getStageName()).isEqualTo("自定义阶段");
    }

    @Test
    void shouldHaveDefaultValidate() {
        TestStageConfig config = new TestStageConfig(true);

        ValidationResult result = config.validate();
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }
}

