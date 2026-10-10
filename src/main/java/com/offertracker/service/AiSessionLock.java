package com.offertracker.service;

import java.util.function.LongFunction;
import java.util.function.Supplier;

/** Coordinates one mutating interview operation for a session. */
public interface AiSessionLock {

    /** 持锁执行操作，不携带 fencing 令牌。 */
    <T> T execute(Long sessionId, Supplier<T> operation);

    /**
     * 持锁执行操作，并把一个单调递增的 fencing 令牌交给调用方。
     *
     * <p>令牌必须在<b>拿到锁之后</b>通过 {@link AiSessionFence#issue(Long)} 领取，
     * 调用方再把它带到存储层的条件写入上。这样才能覆盖“锁租约已过期但原持有者仍在跑”的窗口。</p>
     */
    <T> T executeFenced(Long sessionId, LongFunction<T> operation);
}
