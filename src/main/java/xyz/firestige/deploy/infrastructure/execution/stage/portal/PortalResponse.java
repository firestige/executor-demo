package xyz.firestige.deploy.infrastructure.execution.stage.portal;

/**
 * Portal 响应模型
 *
 * @since RF-19-04
 */
public class PortalResponse {

    private String code;  // "0" 表示成功
    private String msg;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}

