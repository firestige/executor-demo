package xyz.firestige.deploy.infrastructure.execution.stage.asbc;

import java.util.List;

/**
 * ASBC 响应数据
 *
 * @since RF-19-02
 */
public class ASBCResponseData {

    private List<ASBCResultItem> successList;
    private List<ASBCResultItem> failList;

    public List<ASBCResultItem> getSuccessList() {
        return successList;
    }

    public void setSuccessList(List<ASBCResultItem> successList) {
        this.successList = successList;
    }

    public List<ASBCResultItem> getFailList() {
        return failList;
    }

    public void setFailList(List<ASBCResultItem> failList) {
        this.failList = failList;
    }
}

