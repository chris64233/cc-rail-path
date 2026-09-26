package com.chris64233.railpath.domain;

/**
 * 径路节点（径路段）类型：占用区间或占用车站到发线。
 */
public enum LegType {
    /** 区间占用 */
    SECTION,
    /** 车站到发线占用（停靠） */
    STATION
}
