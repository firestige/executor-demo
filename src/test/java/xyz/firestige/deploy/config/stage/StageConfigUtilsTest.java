package xyz.firestige.deploy.config.stage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StageConfigUtils 单元测试
 */
class StageConfigUtilsTest {

    @Test
    void shouldConvertCamelCaseToKebabCase() {
        assertThat(StageConfigUtils.toKebabCase("blueGreenGateway"))
            .isEqualTo("blue-green-gateway");

        assertThat(StageConfigUtils.toKebabCase("portal"))
            .isEqualTo("portal");

        assertThat(StageConfigUtils.toKebabCase("asbcGateway"))
            .isEqualTo("asbc-gateway");
    }

    @Test
    void shouldHandleNullAndEmpty() {
        assertThat(StageConfigUtils.toKebabCase(null)).isNull();
        assertThat(StageConfigUtils.toKebabCase("")).isEmpty();
    }

    @Test
    void shouldConvertKebabCaseToCamelCase() {
        assertThat(StageConfigUtils.toCamelCase("blue-green-gateway"))
            .isEqualTo("blueGreenGateway");

        assertThat(StageConfigUtils.toCamelCase("portal"))
            .isEqualTo("portal");

        assertThat(StageConfigUtils.toCamelCase("asbc-gateway"))
            .isEqualTo("asbcGateway");
    }

    @Test
    void shouldHandleNullAndEmptyForCamelCase() {
        assertThat(StageConfigUtils.toCamelCase(null)).isNull();
        assertThat(StageConfigUtils.toCamelCase("")).isEmpty();
    }

    @Test
    void shouldBeReversible() {
        String original = "blueGreenGateway";
        String kebab = StageConfigUtils.toKebabCase(original);
        String backToCamel = StageConfigUtils.toCamelCase(kebab);

        assertThat(backToCamel).isEqualTo(original);
    }
}

