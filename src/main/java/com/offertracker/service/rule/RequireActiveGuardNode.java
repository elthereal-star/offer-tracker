package com.offertracker.service.rule;

import com.offertracker.common.BusinessException;
import com.offertracker.service.AiInterviewStateMachine;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 守卫：会话必须处于可变更状态。
 *
 * <p>判定委托给 {@link AiInterviewStateMachine#requireActive} —— 状态机是唯一入口，
 * 这里只负责把异常翻译成裁决结论，不重复实现状态判断。</p>
 */
@LiteflowComponent("requireActiveGuard")
public class RequireActiveGuardNode extends NodeComponent {

    @Autowired
    private AiInterviewStateMachine stateMachine;

    @Override
    public void process() {
        AiFollowUpContext context = this.getContextBean(AiFollowUpContext.class);
        if (context.decided()) {
            return;
        }
        try {
            stateMachine.requireActive(context.getSession().getStatus());
        } catch (BusinessException ex) {
            context.reject(ex.getCode(), ex.getMessage());
        }
    }
}
