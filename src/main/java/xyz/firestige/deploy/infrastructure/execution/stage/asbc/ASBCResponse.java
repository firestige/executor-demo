package xyz.firestige.deploy.infrastructure.execution.stage.asbc;

import java.util.List;

/**
 * ASBC 响应模型
 *
 * @since RF-19-02
 */
public class ASBCResponse {

    private Integer code;
    private String msg;
    private ASBCResponseData data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public ASBCResponseData getData() {
        return data;
    }

    public void setData(ASBCResponseData data) {
        this.data = data;
    }
}

