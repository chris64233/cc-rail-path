package com.chris64233.railpath.domain;

/**
 * 径路审批状态。
 */
public enum ReservationStatus {
    /** 已批准、径路有效 */
    ACTIVE,
    /** 已取消（占用全部释放） */
    CANCELLED
}
