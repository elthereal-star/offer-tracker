package com.offertracker.service;

/**
 * 会话级 fencing token 发号器。
 *
 * <p>令牌在每次成功持有会话锁之后领取，且对同一会话严格单调递增。
 * 写入侧必须把令牌一并带到存储层，由条件更新拒绝过期持有者：
 * 锁租约到期后原持有者仍可能继续执行，若不做校验，它的回写会覆盖更晚持有者的结果。</p>
 *
 * <p>发号本身只负责“有序”，真正的互斥保证在存储端的条件写入上。</p>
 */
public interface AiSessionFence {

    /** 为会话领取一个新令牌；同一会话内严格递增。sessionId 为空时返回 0 表示不参与防护。 */
    long issue(Long sessionId);

    /** 判断给定令牌是否仍是该会话已发出的最大令牌。 */
    boolean isCurrent(Long sessionId, long token);
}
