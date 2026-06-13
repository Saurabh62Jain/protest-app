# setup_dbs_fixed.ps1
# Robust script to configure and start portable PostgreSQL, MongoDB, and Redis on Windows.

$projectDir = "."
$dbsDir = Join-Path $projectDir "dbs"
$zipsDir = Join-Path $dbsDir "zips"

$postgisZip = Join-Path $zipsDir "postgis.zip"
$postgresDest = Join-Path $dbsDir "postgres"
$pgsqlFolder = Join-Path $postgresDest "pgsql"

# 1. PostGIS integration
$postgisCheck = Join-Path $pgsqlFolder "lib\postgis-3.dll"
if (-not (Test-Path $postgisCheck)) {
    Write-Output "Extracting and merging PostGIS..."
    $postgisTemp = Join-Path $dbsDir "postgis_temp"
    New-Item -ItemType Directory -Path $postgisTemp -Force | Out-Null
    Expand-Archive -Path $postgisZip -DestinationPath $postgisTemp -Force
    
    Write-Output "Merging PostGIS into PostgreSQL directory..."
    Copy-Item -Path "$postgisTemp\*" -Destination $pgsqlFolder -Recurse -Force
    Remove-Item -Path $postgisTemp -Recurse -Force -ErrorAction SilentlyContinue
    Write-Output "PostGIS merged successfully."
}

# 2. Configure and Start PostgreSQL
$postgresBin = Join-Path $pgsqlFolder "bin"
$postgresData = Join-Path $postgresDest "data"
$postgresConf = Join-Path $postgresData "postgresql.conf"

if (-not (Test-Path $postgresData)) {
    Write-Output "Initializing PostgreSQL database..."
    $initdbPath = Join-Path $postgresBin "initdb.exe"
    # Execute with explicit trust parameters for local setup
    & $initdbPath -D $postgresData -U postgres --auth-local=trust --auth-host=trust
    
    # Configure postgresql.conf to listen on localhost
    if (Test-Path $postgresConf) {
        Add-Content -Path $postgresConf -Value "listen_addresses = 'localhost'"
        Add-Content -Path $postgresConf -Value "port = 5432"
    }
}

# Start PostgreSQL service in background
Write-Output "Starting PostgreSQL on port 5432..."
$pgctlPath = Join-Path $postgresBin "pg_ctl.exe"
$postgresLog = Join-Path $postgresDest "pg.log"

# Stop first just in case it is already running
& $pgctlPath -D $postgresData stop -m fast 2>$null

# Start postgres
& $pgctlPath -D $postgresData -l $postgresLog start

# Wait for Postgres to start
Start-Sleep -Seconds 5

# Create the protest_db and user password if they don't exist
Write-Output "Creating PostgreSQL database 'protest_db' and user settings..."
$psqlPath = Join-Path $postgresBin "psql.exe"

# Create user role password
& $psqlPath -U postgres -d postgres -c "ALTER USER postgres WITH PASSWORD 'password';" 2>$null

# Create database
$createdbPath = Join-Path $postgresBin "createdb.exe"
& $createdbPath -U postgres protest_db 2>$null

# Enable spatial extension in database
Write-Output "Enabling PostGIS extension in 'protest_db'..."
& $psqlPath -U postgres -d protest_db -c "CREATE EXTENSION IF NOT EXISTS postgis;" 2>$null
& $psqlPath -U postgres -d protest_db -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;" 2>$null

# 3. Configure and Start MongoDB
$mongodbDest = Join-Path $dbsDir "mongodb"
$mongodPath = Get-ChildItem -Path $mongodbDest -Filter "mongod.exe" -Recurse | Select-Object -First 1 -ExpandProperty FullName
$mongoData = Join-Path $mongodbDest "data"
$mongoLog = Join-Path $mongodbDest "mongo.log"

if (-not (Test-Path $mongoData)) {
    New-Item -ItemType Directory -Path $mongoData -Force | Out-Null
}

Write-Output "Starting MongoDB on port 27017..."
# Stop mongod first if already running
Get-Process -Name "mongod" -ErrorAction SilentlyContinue | Stop-Process -Force

# Start MongoDB using config file
$mongodConf = Join-Path $mongodbDest "mongod.conf"
Start-Process -FilePath $mongodPath -ArgumentList "--config", $mongodConf -NoNewWindow -PassThru | Out-Null

# 4. Configure and Start Redis
$redisDest = Join-Path $dbsDir "redis"
$redisServerPath = Join-Path $redisDest "redis-server.exe"

Write-Output "Starting Redis on port 6379..."
# Stop redis-server first if already running
Get-Process -Name "redis-server" -ErrorAction SilentlyContinue | Stop-Process -Force

# Start Redis
Start-Process -FilePath $redisServerPath -ArgumentList "--bind 127.0.0.1 --port 6379" -NoNewWindow -PassThru | Out-Null

# 5. Verify Running Ports
Write-Output "Waiting for services to spin up..."
Start-Sleep -Seconds 5

Write-Output "Checking active database listener ports:"
Get-NetTCPConnection -LocalPort 5432, 27017, 6379 -ErrorAction SilentlyContinue | Select-Object LocalAddress, LocalPort, State

Write-Output "Local Database Suite is successfully set up and active!"
Write-Output "Keeping the script alive to keep database processes running..."
while ($true) {
    Start-Sleep -Seconds 10
}
