package xyz.firestige.deploy.infrastructure.execution.stage.asbc;

/**
 * ASBC 结果项
 *
 * @since RF-19-02
 */
public class ASBCResultItem {

    private Integer code;
    private String msg;
    private String calledNumberMatch;
    private String targetTrunkGroupName;

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

    public String getCalledNumberMatch() {
        return calledNumberMatch;
    }

    public void setCalledNumberMatch(String calledNumberMatch) {
        this.calledNumberMatch = calledNumberMatch;
    }

    public String getTargetTrunkGroupName() {
        return targetTrunkGroupName;
    }

    public void setTargetTrunkGroupName(String targetTrunkGroupName) {
        this.targetTrunkGroupName = targetTrunkGroupName;
    }
}

