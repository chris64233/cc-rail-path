package com.chris64233.railpath.api;

import com.chris64233.railpath.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 区间与车站资源接口。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping("/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceService.SectionView registerSection(@Valid @RequestBody SectionRequest request) {
        return toView(resourceService.registerSection(request));
    }

    @GetMapping("/sections")
    public List<ResourceService.SectionView> listSections() {
        return resourceService.listSections().stream().map(ResourceController::toView).toList();
    }

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceService.StationView registerStation(@Valid @RequestBody StationRequest request) {
        return toView(resourceService.registerStation(request));
    }

    @GetMapping("/stations")
    public List<ResourceService.StationView> listStations() {
        return resourceService.listStations().stream().map(ResourceController::toView).toList();
    }

    private static ResourceService.SectionView toView(com.chris64233.railpath.domain.RailSection s) {
        return new ResourceService.SectionView(s.getCode(), s.getDirection(),
                s.getCapacity(), s.getMinHeadwaySeconds());
    }

    private static ResourceService.StationView toView(com.chris64233.railpath.domain.Station s) {
        return new ResourceService.StationView(s.getCode(), s.getTrackCount());
    }
}
