-- 题目编号在会话内必须唯一。
--
-- 追问之前的做法是「查最新题号 + 1 再插入」，这在锁租约过期、两个持有者并发出题时
-- 会插出两道 question_no 相同的题。AI 面试的题目序号直接用于前端展示与「下一题」
-- 判定（requireLatestQuestionGuard 比较的就是 question_no），重复编号会让追问短路
-- 逻辑失效，所以这里把它变成数据库层的硬约束。
--
-- 建约束前先清理历史上可能已经产生的重复行：同 (session_id, question_no) 只保留 id
-- 最小的一条。用派生表包一层是为了兼容 MySQL（不允许在 DELETE 的子查询里直接引用
-- 被删表本身）。
DELETE FROM ai_interview_questions
 WHERE id NOT IN (
   SELECT id FROM (
     SELECT MIN(id) AS id FROM ai_interview_questions GROUP BY session_id, question_no
   ) AS keep_rows
 );

ALTER TABLE ai_interview_questions
  ADD CONSTRAINT uk_ai_interview_questions_no UNIQUE (session_id, question_no);
