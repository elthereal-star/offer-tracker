-- 会话级 fencing token：每次成功获取会话锁时单调递增领取，
-- 写入侧通过条件更新（fence_token < token）拒绝被更晚持有者取代的过期写入。
-- 默认 0 保证既有数据可被任意新令牌推进。
ALTER TABLE ai_interview_sessions ADD COLUMN fence_token BIGINT NOT NULL DEFAULT 0;
