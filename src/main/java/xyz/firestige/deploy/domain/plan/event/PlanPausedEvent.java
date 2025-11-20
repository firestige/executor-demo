package xyz.firestige.deploy.domain.plan.event;

import xyz.firestige.deploy.domain.plan.PlanInfo;
import xyz.firestige.deploy.domain.plan.PlanStatus;
import xyz.firestige.deploy.domain.shared.vo.PlanId;

/**
 * Plan 暂停事件
 * RF-11: 当 Plan 暂停执行时产生
 */
public class PlanPausedEvent extends PlanStatusEvent {

    public PlanPausedEvent(PlanInfo info) {
        super(info, "Plan 已暂停");
    }
}
