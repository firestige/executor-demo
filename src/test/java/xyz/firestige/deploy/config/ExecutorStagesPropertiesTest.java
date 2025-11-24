package xyz.firestige.deploy.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import xyz.firestige.deploy.config.ASBCGatewayStageConfig;
import xyz.firestige.deploy.config.BlueGreenGatewayStageConfig;
import xyz.firestige.deploy.config.PortalStageConfig;
import xyz.firestige.deploy.config.stage.StageConfigurable;
import xyz.firestige.deploy.config.stage.ValidationResult;

import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExecutorStagesProperties Phase2 测试
 */
@SpringBootTest(classes = {xyz.firestige.deploy.autoconfigure.ExecutorStagesAutoConfiguration.class})
@TestPropertySource(properties = {
        "executor.stages.blue-green-gateway.enabled=true",
        "executor.stages.asbc-gateway.enabled=false"
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
        assertThat(properties.isStageEnabled("portal")).isTrue();
        assertThat(properties.isStageEnabled("asbc-gateway")).isFalse();
    }

    @Test
    void shouldProvideEnabledStagesMap() {
        Map<String, StageConfigurable> enabled = properties.getEnabledStages();
        assertThat(enabled.keySet()).containsExactlyInAnyOrder("blue-green-gateway", "portal");
    }

    @Test
    void shouldReturnNullForUnknownStage() {
        assertThat(properties.getStage("not-exists", StageConfigurable.class)).isNull();
    }

    @Test
    void validateShouldAutofixMissingValues() {
        BlueGreenGatewayStageConfig bg = properties.getBlueGreenGateway();
        // force invalid values
        bg.setHealthCheckPath(" ");
        bg.setHealthCheckIntervalSeconds(0);
        bg.setHealthCheckMaxAttempts(-1);
        bg.setSteps(new ArrayList<>());
        ValidationResult r = bg.validate();
        assertThat(r.getWarnings()).isNotEmpty();
        assertThat(bg.getHealthCheckPath()).isEqualTo("/health");
        assertThat(bg.getHealthCheckIntervalSeconds()).isEqualTo(3);
        assertThat(bg.getHealthCheckMaxAttempts()).isEqualTo(10);
        assertThat(bg.getSteps()).isNotEmpty();
    }

    @Test
    void portalShouldFillDefaultSteps() {
        PortalStageConfig portal = properties.getPortal();
        portal.setSteps(new ArrayList<>()); // clear
        ValidationResult r = portal.validate();
        assertThat(r.getWarnings()).isNotEmpty();
        assertThat(portal.getSteps()).isNotEmpty();
    }

    @Test
    void asbcDisabledShouldSkipValidation() {
        ASBCGatewayStageConfig asbc = properties.getAsbcGateway();
        assertThat(asbc.isEnabled()).isFalse();
        ValidationResult r = asbc.validate();
        assertThat(r.getWarnings()).isEmpty();
    }
}
