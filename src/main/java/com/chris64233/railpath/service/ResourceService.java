package com.chris64233.railpath.service;

import com.chris64233.railpath.api.SectionRequest;
import com.chris64233.railpath.api.StationRequest;
import com.chris64233.railpath.domain.Direction;
import com.chris64233.railpath.domain.RailSection;
import com.chris64233.railpath.domain.Station;
import com.chris64233.railpath.error.ApiException;
import com.chris64233.railpath.error.ErrorType;
import com.chris64233.railpath.repository.RailSectionRepository;
import com.chris64233.railpath.repository.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 区间与车站基础资源的注册与查询。
 */
@Service
public class ResourceService {

    private final RailSectionRepository sectionRepository;
    private final StationRepository stationRepository;

    public ResourceService(RailSectionRepository sectionRepository,
                           StationRepository stationRepository) {
        this.sectionRepository = sectionRepository;
        this.stationRepository = stationRepository;
    }

    @Transactional
    public RailSection registerSection(SectionRequest request) {
        if (sectionRepository.existsById(request.code())) {
            throw new ApiException(ErrorType.CONFLICT, "CONFLICT_SECTION_EXISTS",
                    "区间代码已存在：" + request.code());
        }
        return sectionRepository.save(new RailSection(request.code(), request.direction(),
                request.capacity(), request.minHeadwaySeconds()));
    }

    @Transactional
    public Station registerStation(StationRequest request) {
        if (stationRepository.existsById(request.code())) {
            throw new ApiException(ErrorType.CONFLICT, "CONFLICT_STATION_EXISTS",
                    "车站代码已存在：" + request.code());
        }
        return stationRepository.save(new Station(request.code(), request.trackCount()));
    }

    @Transactional(readOnly = true)
    public List<RailSection> listSections() {
        return sectionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Station> listStations() {
        return stationRepository.findAll();
    }

    /** 供控制器/视图使用的只读记录。 */
    public record SectionView(String code, Direction direction, int capacity, long minHeadwaySeconds) {
    }

    public record StationView(String code, int trackCount) {
    }
}
