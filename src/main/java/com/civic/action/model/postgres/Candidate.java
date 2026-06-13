package com.civic.action.model.postgres;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "candidates")
@Data
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String party;

    @Column(name = "boundary_code", nullable = false)
    private String boundaryCode; // Matches GeoBoundary.code (e.g. Ward Number or Constituency ID)

    private String constituencyName;

    private String designation; // Populated designation e.g. "MLA", "MP", "Councillor"

    private String profilePhotoUrl;

    @Column(columnDefinition = "TEXT")
    private String biography;

    @Column(columnDefinition = "TEXT")
    private String contactDetails;

    private String country;
}
