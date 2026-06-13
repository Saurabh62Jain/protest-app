-- Enable the PostGIS Extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Create users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    mobile_number VARCHAR(50) UNIQUE,
    email VARCHAR(100) UNIQUE,
    google_id VARCHAR(100) UNIQUE,
    voter_id VARCHAR(50) UNIQUE,
    role VARCHAR(50) NOT NULL,
    designation VARCHAR(100),
    home_location GEOMETRY(Point, 4326),
    shadowbanned BOOLEAN NOT NULL DEFAULT FALSE
);

-- Create spatial index for fast user coordinate lookup
CREATE INDEX IF NOT EXISTS idx_users_spatial_location 
ON users USING gist (home_location);


-- 2. Create geo_boundaries table
CREATE TABLE IF NOT EXISTS geo_boundaries (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    country VARCHAR(100) NOT NULL,
    boundary_geometry GEOMETRY(MultiPolygon, 4326) NOT NULL
);

-- Create spatial index on boundary shapes
CREATE INDEX IF NOT EXISTS idx_geo_boundaries_spatial 
ON geo_boundaries USING gist (boundary_geometry);


-- 3. Create candidates table
CREATE TABLE IF NOT EXISTS candidates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    party VARCHAR(100),
    boundary_code VARCHAR(100) NOT NULL,
    constituency_name VARCHAR(255),
    designation VARCHAR(100),
    profile_photo_url VARCHAR(512),
    biography TEXT,
    contact_details TEXT,
    country VARCHAR(100)
);

-- Insert dummy data for testing if tables are empty
-- Ward 1 shape in Bhopal (simplistic bounding box containing 23.2599, 77.4126)
INSERT INTO geo_boundaries (name, code, type, country, boundary_geometry)
SELECT 'Bhopal Ward 1', 'WARD-01', 'WARD', 'India', 
       ST_GeomFromText('MULTIPOLYGON(((77.30 23.20, 77.50 23.20, 77.50 23.40, 77.30 23.40, 77.30 23.20)))', 4326)
WHERE NOT EXISTS (SELECT 1 FROM geo_boundaries WHERE code = 'WARD-01');

-- Force update if WARD-01 already exists from prior seeds to switch from Delhi to Bhopal
UPDATE geo_boundaries 
SET name = 'Bhopal Ward 1', 
    boundary_geometry = ST_GeomFromText('MULTIPOLYGON(((77.30 23.20, 77.50 23.20, 77.50 23.40, 77.30 23.40, 77.30 23.20)))', 4326)
WHERE code = 'WARD-01';

-- Candidate for Ward 1
INSERT INTO candidates (name, party, boundary_code, constituency_name, designation, country)
SELECT 'Saurabh Kumar', 'Independent', 'WARD-01', 'Bhopal Ward 1', 'Ward Representative', 'India'
WHERE NOT EXISTS (SELECT 1 FROM candidates WHERE boundary_code = 'WARD-01');

UPDATE candidates
SET constituency_name = 'Bhopal Ward 1'
WHERE boundary_code = 'WARD-01';
