package com.chris64233.railpath.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.railpath.service.PathService;
import com.chris64233.railpath.web.dto.EventView;
import com.chris64233.railpath.web.dto.PathApplyRequest;
import com.chris64233.railpath.web.dto.PathResponse;
import com.chris64233.railpath.web.dto.RerouteRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/paths")
public class PathController {

    private final PathService paths;

    public PathController(PathService paths) {
        this.paths = paths;
    }

    /** 申请径路；按外部运行号幂等，重放返回原结果。 */
    @PostMapping
    public ResponseEntity<PathResponse> apply(@Valid @RequestBody PathApplyRequest request) {
        PathService.ApplyOutcome outcome = paths.apply(request);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.path());
    }

    /** 查询径路完整占用明细。 */
    @GetMapping("/{externalRef}")
    public PathResponse detail(@PathVariable String externalRef) {
        return paths.getPath(externalRef);
    }

    /** 查询径路的不可变事件流。 */
    @GetMapping("/{externalRef}/events")
    public List<EventView> events(@PathVariable String externalRef) {
        return paths.getEvents(externalRef);
    }

    /** 整体改线：新径路完整取得后才释放旧径路。 */
    @PostMapping("/{externalRef}/reroute")
    public PathResponse reroute(@PathVariable String externalRef,
            @Valid @RequestBody RerouteRequest request) {
        return paths.reroute(externalRef, request);
    }

    /** 取消径路并释放全部占用。 */
    @PostMapping("/{externalRef}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String externalRef) {
        paths.cancel(externalRef);
    }
}
