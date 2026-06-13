package com.civic.action.model.postgres;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String mobileNumber;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String googleId;

    // Required to raise an issue
    @Column(unique = true)
    private String voterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // ROLE_CITIZEN, ROLE_APPROVER, ROLE_MAINTENANCE, ROLE_ADMIN

    private String designation; // Populated only if role == ROLE_APPROVER

    // Hibernate Spatial type mapping directly to a PostGIS Point
    @Column(name = "home_location", columnDefinition = "geometry(Point, 4326)")
    private Point homeLocation;

    @Column(nullable = false)
    private boolean shadowbanned = false;
}
