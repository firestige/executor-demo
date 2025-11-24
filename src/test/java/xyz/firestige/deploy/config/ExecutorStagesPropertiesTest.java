package xyz.firestige.deploy.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import xyz.firestige.deploy.config.stage.StageConfigurable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExecutorStagesProperties Phase2 测试
 */
@SpringBootTest(classes = {xyz.firestige.deploy.autoconfigure.ExecutorStagesAutoConfiguration.class})
@TestPropertySource(properties = {
        "executor.stages.blue-green-gateway.enabled=true",
        "executor.stages.portal.enabled=false",
        "executor.stages.asbc-gateway.enabled=true"
})
class ExecutorStagesPropertiesTest {

    @Autowired
    private ExecutorStagesProperties properties;

    @Test
    void shouldAutoDiscoverAllStages() {
        Map<String, StageConfigurable> all = properties.getAllStages();
        assertThat(all).containsKeys("blue-green-gateway", "portal", "asbc-gateway");
    }

    @Test
    void shouldRespectEnabledFlags() {
        assertThat(properties.isStageEnabled("blue-green-gateway")).isTrue();
        assertThat(properties.isStageEnabled("portal")).isFalse();
        assertThat(properties.isStageEnabled("asbc-gateway")).isTrue();
    }

    @Test
    void shouldProvideEnabledStagesMap() {
        Map<String, StageConfigurable> enabled = properties.getEnabledStages();
        assertThat(enabled.keySet()).containsExactlyInAnyOrder("blue-green-gateway", "asbc-gateway");
    }

    @Test
    void shouldReturnNullForUnknownStage() {
        assertThat(properties.getStage("not-exists", StageConfigurable.class)).isNull();
    }
}
