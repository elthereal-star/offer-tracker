package com.offertracker.service.rule;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

/** 守卫：追问必须建立在已完成的评分之上，否则出的题没有针对性。 */
@LiteflowComponent("requireEvaluationGuard")
public class RequireEvaluationGuardNode extends NodeComponent {

    @Override
    public void process() {
        AiFollowUpContext context = this.getContextBean(AiFollowUpContext.class);
        if (context.decided()) {
            return;
        }
        if (context.getPrevious().getScore() == null || context.getPrevious().getFeedback() == null) {
            context.reject(409, "请先完成当前题目的 AI 评分");
        }
    }
}
