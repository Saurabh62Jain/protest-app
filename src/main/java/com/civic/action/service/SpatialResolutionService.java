package com.civic.action.service;

import com.civic.action.model.postgres.BoundaryType;
import com.civic.action.model.postgres.GeoBoundary;
import com.civic.action.repository.postgres.GeoBoundaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpatialResolutionService {

    private final GeoBoundaryRepository geoBoundaryRepository;
    
    // Geometry factory using WGS-84 standard (SRID 4326) coordinates format
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional(readOnly = true)
    public Map<String, GeoBoundary> resolvePoliticalHierarchy(double latitude, double longitude) {
        // JTS Points represent coordinates as X (Longitude) and Y (Latitude)
        Point userLocation = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        
        Map<String, GeoBoundary> hierarchy = new HashMap<>();

        // 1. Resolve Ward
        resolveBoundary(userLocation, BoundaryType.WARD)
                .ifPresent(boundary -> hierarchy.put("ward", boundary));

        // 2. Resolve Legislative Assembly (Vidhan Sabha)
        resolveBoundary(userLocation, BoundaryType.VIDHAN_SABHA)
                .ifPresent(boundary -> hierarchy.put("vidhanSabha", boundary));

        // 3. Resolve Parliamentary Constituency (Lok Sabha)
        resolveBoundary(userLocation, BoundaryType.LOK_SABHA)
                .ifPresent(boundary -> hierarchy.put("lokSabha", boundary));

        log.info("Resolved coordinates [lat={}, lon={}] to boundaries: {}", 
                latitude, longitude, hierarchy.keySet());
        
        return hierarchy;
    }

    private Optional<GeoBoundary> resolveBoundary(Point point, BoundaryType type) {
        // Attempt HQL spatial query first
        try {
            return geoBoundaryRepository.findContainingBoundary(point, type);
        } catch (Exception e) {
            log.warn("Standard spatial lookup failed, attempting native PostGIS query. Error: {}", e.getMessage());
            try {
                return geoBoundaryRepository.findContainingBoundaryNative(point, type);
            } catch (Exception ex) {
                log.error("Failed native PostGIS resolution for boundary type: {}", type, ex);
                return Optional.empty();
            }
        }
    }
}
