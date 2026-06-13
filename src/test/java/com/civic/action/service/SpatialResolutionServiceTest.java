package com.civic.action.service;

import com.civic.action.model.postgres.BoundaryType;
import com.civic.action.model.postgres.GeoBoundary;
import com.civic.action.repository.postgres.GeoBoundaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SpatialResolutionServiceTest {

    @Mock
    private GeoBoundaryRepository geoBoundaryRepository;

    @InjectMocks
    private SpatialResolutionService spatialResolutionService;

    private GeometryFactory geometryFactory;

    @BeforeEach
    public void setUp() {
        geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    @Test
    public void testResolvePoliticalHierarchySuccess() {
        double lat = 28.6139; // Delhi Latitude
        double lon = 77.2090; // Delhi Longitude
        Point mockPoint = geometryFactory.createPoint(new Coordinate(lon, lat));

        GeoBoundary ward = new GeoBoundary();
        ward.setCode("WARD-01");
        ward.setType(BoundaryType.WARD);

        GeoBoundary vs = new GeoBoundary();
        vs.setCode("VS-12");
        vs.setType(BoundaryType.VIDHAN_SABHA);

        GeoBoundary ls = new GeoBoundary();
        ls.setCode("LS-05");
        ls.setType(BoundaryType.LOK_SABHA);

        when(geoBoundaryRepository.findContainingBoundary(any(Point.class), eq(BoundaryType.WARD)))
                .thenReturn(Optional.of(ward));
        when(geoBoundaryRepository.findContainingBoundary(any(Point.class), eq(BoundaryType.VIDHAN_SABHA)))
                .thenReturn(Optional.of(vs));
        when(geoBoundaryRepository.findContainingBoundary(any(Point.class), eq(BoundaryType.LOK_SABHA)))
                .thenReturn(Optional.of(ls));

        Map<String, GeoBoundary> result = spatialResolutionService.resolvePoliticalHierarchy(lat, lon);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("WARD-01", result.get("ward").getCode());
        assertEquals("VS-12", result.get("vidhanSabha").getCode());
        assertEquals("LS-05", result.get("lokSabha").getCode());
    }
}
