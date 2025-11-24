package xyz.firestige.deploy.config.stage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ValidationResult 单元测试
 */
class ValidationResultTest {

    @Test
    void shouldCreateSuccessResult() {
        ValidationResult result = ValidationResult.success();

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldCreateWarningResult() {
        ValidationResult result = ValidationResult.warning("Test warning");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings()).contains("Test warning");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void shouldCreateErrorResult() {
        ValidationResult result = ValidationResult.error("Test error");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).contains("Test error");
    }

    @Test
    void shouldBuildWithMultipleWarnings() {
        ValidationResult result = ValidationResult.builder()
            .warning("Warning 1")
            .warning("Warning 2")
            .build();

        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).hasSize(2);
        assertThat(result.getWarnings()).containsExactly("Warning 1", "Warning 2");
    }

    @Test
    void shouldBuildWithMultipleErrors() {
        ValidationResult result = ValidationResult.builder()
            .error("Error 1")
            .error("Error 2")
            .build();

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors()).containsExactly("Error 1", "Error 2");
    }

    @Test
    void shouldBuildWithBothWarningsAndErrors() {
        ValidationResult result = ValidationResult.builder()
            .warning("Warning 1")
            .error("Error 1")
            .warning("Warning 2")
            .build();

        assertThat(result.isValid()).isFalse();
        assertThat(result.getWarnings()).hasSize(2);
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    void shouldAddListOfWarnings() {
        ValidationResult result = ValidationResult.builder()
            .warnings(List.of("Warning 1", "Warning 2", "Warning 3"))
            .build();

        assertThat(result.getWarnings()).hasSize(3);
    }

    @Test
    void shouldAddListOfErrors() {
        ValidationResult result = ValidationResult.builder()
            .errors(List.of("Error 1", "Error 2"))
            .build();

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    void shouldBeImmutable() {
        ValidationResult result = ValidationResult.builder()
            .warning("Warning")
            .build();

        // 获取的列表应该是不可变的
        assertThat(result.getWarnings())
            .isUnmodifiable();
        assertThat(result.getErrors())
            .isUnmodifiable();
    }

    @Test
    void shouldHaveToString() {
        ValidationResult result = ValidationResult.builder()
            .warning("Warning")
            .error("Error")
            .build();

        String str = result.toString();
        assertThat(str).contains("valid=false");
        assertThat(str).contains("warnings=1");
        assertThat(str).contains("errors=1");
    }
}

