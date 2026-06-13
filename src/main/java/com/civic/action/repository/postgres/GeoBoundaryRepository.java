package com.civic.action.repository.postgres;

import com.civic.action.model.postgres.GeoBoundary;
import com.civic.action.model.postgres.BoundaryType;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GeoBoundaryRepository extends JpaRepository<GeoBoundary, Long> {

    /**
     * Finds the boundary of a specific type that contains the given coordinate point.
     * Uses Hibernate Spatial's 'within' abstraction.
     */
    @Query("SELECT gb FROM GeoBoundary gb WHERE gb.type = :type AND within(:point, gb.boundaryGeometry) = true")
    Optional<GeoBoundary> findContainingBoundary(@Param("point") Point point, @Param("type") BoundaryType type);

    /**
     * Native query alternative using PostGIS ST_Contains function.
     */
    @Query(value = "SELECT * FROM geo_boundaries WHERE type = :#{#type.name()} AND ST_Contains(boundary_geometry, CAST(:point AS geometry)) = true LIMIT 1", nativeQuery = true)
    Optional<GeoBoundary> findContainingBoundaryNative(@Param("point") Point point, @Param("type") BoundaryType type);
}
