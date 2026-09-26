package com.chris64233.railpath.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.railpath.domain.Section;
import com.chris64233.railpath.repository.SectionRepository;
import com.chris64233.railpath.web.dto.SectionRequest;
import com.chris64233.railpath.web.dto.SectionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionRepository sections;

    public SectionController(SectionRepository sections) {
        this.sections = sections;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SectionResponse create(@Valid @RequestBody SectionRequest request) {
        Section saved = sections.save(new Section(request.code(), request.direction(),
                request.capacity(), request.minHeadwayMinutes()));
        return SectionResponse.from(saved);
    }

    @GetMapping
    public List<SectionResponse> list() {
        return sections.findAll().stream().map(SectionResponse::from).toList();
    }
}
