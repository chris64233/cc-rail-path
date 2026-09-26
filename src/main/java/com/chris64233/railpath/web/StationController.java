package com.chris64233.railpath.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.railpath.domain.Station;
import com.chris64233.railpath.repository.StationRepository;
import com.chris64233.railpath.web.dto.StationRequest;
import com.chris64233.railpath.web.dto.StationResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationRepository stations;

    public StationController(StationRepository stations) {
        this.stations = stations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StationResponse create(@Valid @RequestBody StationRequest request) {
        Station saved = stations.save(new Station(request.code(), request.trackCount()));
        return StationResponse.from(saved);
    }

    @GetMapping
    public List<StationResponse> list() {
        return stations.findAll().stream().map(StationResponse::from).toList();
    }
}
