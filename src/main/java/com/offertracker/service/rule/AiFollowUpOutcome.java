package com.offertracker.service.rule;

/** 追问裁决链的输出结论。 */
public enum AiFollowUpOutcome {

    /** 链尚未给出结论。 */
    PENDING,

    /** 裁决为拒绝，由调用方翻译成对应的业务异常。 */
    REJECTED,

    /** 无需出题（给旧题重复请求追问），幂等短路。 */
    SKIPPED,

    /** 可以继续出下一道追问。 */
    READY
}
