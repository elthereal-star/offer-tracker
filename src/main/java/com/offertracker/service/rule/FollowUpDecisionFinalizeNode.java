package com.offertracker.service.rule;

import com.offertracker.entity.AiInterviewQuestion;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

/** 终节点：全部守卫通过后计算下一题号并组装提示词。 */
@LiteflowComponent("followUpDecisionFinalize")
public class FollowUpDecisionFinalizeNode extends NodeComponent {

    @Override
    public void process() {
        AiFollowUpContext context = this.getContextBean(AiFollowUpContext.class);
        if (context.decided()) {
            return;
        }
        AiInterviewQuestion previous = context.getPrevious();
        int nextQuestionNo = context.getLatestQuestionNo() + 1;
        String prompt = "请根据上一道面试题、候选人回答和评分反馈，生成下一道有针对性的追问。只返回题目本身，不要编号、不要解释。上一题：\n"
                + previous.getContent() + "\n回答：\n" + previous.getAnswer() + "\n评分反馈：\n" + previous.getFeedback();
        context.ready(nextQuestionNo, prompt);
    }
}
