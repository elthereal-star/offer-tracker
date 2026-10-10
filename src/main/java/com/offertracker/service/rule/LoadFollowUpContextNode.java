package com.offertracker.service.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.mapper.AiInterviewQuestionMapper;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import org.springframework.beans.factory.annotation.Autowired;

/** 加载追问所需的上下文：上一题实体与当前会话的最新题号。 */
@LiteflowComponent("loadFollowUpContext")
public class LoadFollowUpContextNode extends NodeComponent {

    @Autowired
    private AiInterviewQuestionMapper questions;

    @Override
    public void process() {
        AiFollowUpContext context = this.getContextBean(AiFollowUpContext.class);
        if (context.decided()) {
            return;
        }

        AiInterviewQuestion previous = questions.selectOne(new LambdaQueryWrapper<AiInterviewQuestion>()
                .eq(AiInterviewQuestion::getId, context.getQuestionId())
                .eq(AiInterviewQuestion::getSessionId, context.getSessionId()));
        if (previous == null) {
            context.reject(404, "AI 面试题目不存在: " + context.getQuestionId());
            return;
        }
        context.setPrevious(previous);

        Integer latestNo = questions.selectList(new LambdaQueryWrapper<AiInterviewQuestion>()
                        .eq(AiInterviewQuestion::getSessionId, context.getSessionId()))
                .stream()
                .map(AiInterviewQuestion::getQuestionNo)
                .max(Integer::compareTo)
                .orElse(0);
        context.setLatestQuestionNo(latestNo);
    }
}
