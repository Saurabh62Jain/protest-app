package com.civic.action.model.postgres;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.MultiPolygon;

@Entity
@Table(name = "geo_boundaries", indexes = {
    @Index(name = "idx_spatial_boundary", columnList = "boundary_geometry")
})
@Data
public class GeoBoundary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code; // e.g. "WARD-12", "AC-34", "PC-05"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoundaryType type; // WARD, VIDHAN_SABHA, LOK_SABHA

    @Column(nullable = false)
    private String country;

    // Hibernate Spatial geometry type for PostGIS MultiPolygon
    @Column(name = "boundary_geometry", columnDefinition = "geometry(MultiPolygon, 4326)", nullable = false)
    private MultiPolygon boundaryGeometry;
}
