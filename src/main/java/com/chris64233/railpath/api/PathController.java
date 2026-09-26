package com.chris64233.railpath.api;

import com.chris64233.railpath.service.PathService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运行径路接口：批准、整体改线、取消、占用明细与事件查询。
 */
@RestController
@RequestMapping("/api/paths")
public class PathController {

    private final PathService pathService;

    public PathController(PathService pathService) {
        this.pathService = pathService;
    }

    /** 提交运行申请，全部节点校验通过后一次性写入，返回完整径路。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationView approve(@Valid @RequestBody PathRequest request) {
        return pathService.approve(request);
    }

    /** 查询径路完整占用明细（含已取消径路，状态为 CANCELLED）。 */
    @GetMapping("/{externalRunNo}")
    public ReservationView get(@PathVariable String externalRunNo) {
        return pathService.get(externalRunNo);
    }

    /** 尚未发车的径路整体改线；新径路未完整取得前旧径路保持不变。 */
    @PostMapping("/{externalRunNo}/reroute")
    public ReservationView reroute(@PathVariable String externalRunNo,
                                   @Valid @RequestBody RerouteRequest request) {
        return pathService.reroute(externalRunNo, request.legs());
    }

    /** 取消径路，释放全部占用（取消幂等）。 */
    @PostMapping("/{externalRunNo}/cancel")
    public ReservationView cancel(@PathVariable String externalRunNo) {
        return pathService.cancel(externalRunNo);
    }

    /** 查询某条径路的不可变事件（批准/改线/取消）。 */
    @GetMapping("/{externalRunNo}/events")
    public List<EventView> events(@PathVariable String externalRunNo) {
        return pathService.events(externalRunNo);
    }

    /** 查询全部径路事件，按发生顺序返回。 */
    @GetMapping("/events")
    public List<EventView> allEvents() {
        return pathService.allEvents();
    }
}
