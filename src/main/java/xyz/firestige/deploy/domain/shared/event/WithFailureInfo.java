package xyz.firestige.deploy.domain.shared.event;

import xyz.firestige.deploy.domain.shared.exception.FailureInfo;

/**
 * 标记，表示携带 FailureInfo 的事件
 */
public interface WithFailureInfo {
    FailureInfo getFailureInfo();
}
