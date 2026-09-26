package com.chris64233.railpath.service;

import com.chris64233.railpath.api.LegRequest;
import com.chris64233.railpath.api.PathRequest;
import com.chris64233.railpath.domain.LegType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 径路规范化、展开与哈希。
 *
 * <p>每个申请区间段会被展开为一个区间占用，以及（除最后一段外）其后停靠车站的占用，
 * 展开后的占用序列在时间上必须首尾相接、严格有序。
 */
final class Plans {

    /** 规范化后的单个占用节点。 */
    record Occupancy(int seq, LegType type, String code, Instant entry, Instant exit) {
    }

    private Plans() {
    }

    /**
     * 把申请展开为占用序列并做基础校验。
     *
     * <p>相邻两个区间之间：
     * <ul>
     *   <li>给出停靠车站时，车站占用 [上一区间离开, 下一区间进入)，且停靠时长必须为正；</li>
     *   <li>不停靠（通过）时，下一区间必须在上一区间离开的同一时刻进入，时间上不得留空档。</li>
     * </ul>
     *
     * @throws com.chris64233.railpath.error.ApiException 时间不合法、时序不连续等校验错误
     */
    static List<Occupancy> expand(PathRequest request) {
        return expand(request.legs());
    }

    static List<Occupancy> expand(List<LegRequest> legs) {
        List<Occupancy> out = new ArrayList<>(legs.size() * 2);

        for (int i = 0; i < legs.size(); i++) {
            LegRequest leg = legs.get(i);
            boolean last = i == legs.size() - 1;

            if (!leg.exitTime().isAfter(leg.entryTime())) {
                throw validation("legs[" + i + "].exitTime", "离开时间必须晚于进入时间");
            }

            out.add(new Occupancy(out.size(), LegType.SECTION,
                    leg.sectionCode(), leg.entryTime(), leg.exitTime()));

            if (!last) {
                Instant nextEntry = legs.get(i + 1).entryTime();
                boolean stops = leg.stopStationCode() != null && !leg.stopStationCode().isBlank();
                if (stops) {
                    // 停靠：车站占用覆盖从上一区间离开到下一区间进入的整段时间。
                    if (!nextEntry.isAfter(leg.exitTime())) {
                        throw validation("legs[" + i + "].stopStationCode",
                                "车站停靠时间必须为正：下一区间进入时间须晚于本站离开时间");
                    }
                    out.add(new Occupancy(out.size(), LegType.STATION,
                            leg.stopStationCode(), leg.exitTime(), nextEntry));
                } else {
                    // 不停靠、直接通过：下一区间必须在本区间离开的同一时刻进入，时间不得留空档。
                    if (!nextEntry.equals(leg.exitTime())) {
                        throw validation("legs[" + (i + 1) + "].entryTime",
                                "径路必须按时间连续：未安排停靠车站时，下一区间进入时间必须等于上一区间离开时间 "
                                        + leg.exitTime());
                    }
                }
            } else if (leg.stopStationCode() != null && !leg.stopStationCode().isBlank()) {
                throw validation("legs[" + i + "].stopStationCode",
                        "终点站停靠不在本申请范围内，最后一个区间段不应填写停靠车站");
            }
        }
        return out;
    }

    /**
     * 计算申请内容的稳定哈希。仅依赖区间代码、时间和停靠车站的顺序，
     * 与 JSON 字段顺序、空白无关，保证“相同内容重放”判定可靠。
     */
    static String hash(PathRequest request) {
        return hash(request.legs());
    }

    static String hash(List<LegRequest> legs) {
        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < legs.size(); i++) {
            LegRequest leg = legs.get(i);
            canonical.append(i).append('|')
                    .append(leg.sectionCode()).append('|')
                    .append(leg.entryTime()).append('|')
                    .append(leg.exitTime()).append('|')
                    .append(leg.stopStationCode() == null ? "" : leg.stopStationCode())
                    .append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static com.chris64233.railpath.error.ApiException validation(String field, String message) {
        // 复用统一错误模型，字段级校验错误以 CONFLICT 明细不适用，这里走 VALIDATION。
        return new com.chris64233.railpath.error.ApiException(
                com.chris64233.railpath.error.ErrorType.VALIDATION,
                "VALIDATION_PATH_NOT_CONTINUOUS", message,
                List.of());
    }
}
