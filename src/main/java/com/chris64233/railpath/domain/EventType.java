package com.chris64233.railpath.domain;

/**
 * 径路生命周期事件类型。事件只追加、不可修改。
 */
public enum EventType {
    /** 批准 */
    APPROVED,
    /** 整体改线 */
    REROUTED,
    /** 取消 */
    CANCELLED
}
