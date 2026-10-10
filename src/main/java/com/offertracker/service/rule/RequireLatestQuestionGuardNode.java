package com.offertracker.service.rule;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

/**
 * 守卫：只能对最新的一道题追问。
 *
 * <p>对旧题重复请求追问时判定为 SKIPPED 而非拒绝 —— 这是前端重试的幂等语义，
 * 调用方应当直接返回当前会话，而不是报错。</p>
 */
@LiteflowComponent("requireLatestQuestionGuard")
public class RequireLatestQuestionGuardNode extends NodeComponent {

    @Override
    public void process() {
        AiFollowUpContext context = this.getContextBean(AiFollowUpContext.class);
        if (context.decided()) {
            return;
        }
        if (context.getPrevious().getQuestionNo() < context.getLatestQuestionNo()) {
            context.skip();
        }
    }
}
