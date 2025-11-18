package xyz.firestige.deploy.unit.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import xyz.firestige.deploy.exception.ErrorType;
import xyz.firestige.deploy.exception.FailureInfo;
import xyz.firestige.deploy.util.TimingExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FailureInfo 单元测试
 * 测试失败信息封装类
 */
@Tag("unit")
@Tag("fast")
@ExtendWith(TimingExtension.class)
@DisplayName("FailureInfo 单元测试")
class FailureInfoTest {

    @Test
    @DisplayName("场景: 创建基本失败信息")
    void testCreateBasicFailureInfo() {
        // When: 创建失败信息
        FailureInfo info = FailureInfo.of(ErrorType.SYSTEM_ERROR, "系统错误");

        // Then: 属性正确
        assertNotNull(info);
        assertEquals(ErrorType.SYSTEM_ERROR, info.getErrorType());
        assertEquals("系统错误", info.getErrorMessage());
    }

    @Test
    @DisplayName("场景: 不同错误类型")
    void testDifferentErrorTypes() {
        // When: 创建不同类型的失败信息
        FailureInfo validation = FailureInfo.of(ErrorType.VALIDATION_ERROR, "校验失败");
        FailureInfo timeout = FailureInfo.of(ErrorType.TIMEOUT_ERROR, "超时");
        FailureInfo business = FailureInfo.of(ErrorType.BUSINESS_ERROR, "业务错误");

        // Then: 类型正确
        assertEquals(ErrorType.VALIDATION_ERROR, validation.getErrorType());
        assertEquals(ErrorType.TIMEOUT_ERROR, timeout.getErrorType());
        assertEquals(ErrorType.BUSINESS_ERROR, business.getErrorType());
    }
}

