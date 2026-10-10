package com.offertracker.service;

/**
 * 会话级 fencing token 发号器。
 *
 * <p>令牌在每次成功持有会话锁之后领取，且对同一会话严格单调递增。
 * 写入侧必须把令牌一并带到存储层，由条件更新拒绝过期持有者：
 * 锁租约到期后原持有者仍可能继续执行，若不做校验，它的回写会覆盖更晚持有者的结果。</p>
 *
 * <p>发号本身只负责“有序”，真正的互斥保证在存储端的条件写入上。</p>
 *
 * <p><b>实现的必备义务</b>：发出的令牌必须严格大于数据库中已记录的 {@code fence_token}。
 * 发号器自身可以是易失的（进程内计数器、Redis 键），但库里的令牌是持久化的，
 * 两者不对齐就会出现“发出的令牌小于库中值”→ 条件写入恒不成立 → 该会话永久无法收尾。
 * 因此两个实现都在发号前先与 {@code AiInterviewSessionMapper.selectFenceToken} 对齐。</p>
 */
public interface AiSessionFence {

    /** 为会话领取一个新令牌；同一会话内严格递增，且必大于库中已记录值。sessionId 为空时返回 0 表示不参与防护。 */
    long issue(Long sessionId);
}
