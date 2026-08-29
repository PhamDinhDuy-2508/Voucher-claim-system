param(
    [string]$KafkaBootstrapServers = "kafka-1e3841d-duydinh258138-42a5.h.aivencloud.com:12561",
    [string]$KafkaCaCertificatePath = "$PSScriptRoot\..\secrets\aiven\ca.pem",
    [string]$KafkaAccessCertificatePath = "$PSScriptRoot\..\secrets\aiven\service.cert",
    [string]$KafkaAccessKeyPath = "$PSScriptRoot\..\secrets\aiven\service.key",
    [string]$RedisHost = "valkey-168c0ddb-duydinh258138-42a5.h.aivencloud.com",
    [int]$RedisPort = 12560,
    [string]$RedisUsername = "default"
)

if ([string]::IsNullOrWhiteSpace($KafkaBootstrapServers)) {
    throw "Aiven Kafka bootstrap server is required."
}
if ([string]::IsNullOrWhiteSpace($RedisHost) -or $RedisPort -le 0) {
    throw "Aiven Redis host and port are required."
}
if ([string]::IsNullOrWhiteSpace($RedisUsername)) {
    throw "Aiven Redis username is required."
}

$certificateFiles = @(
    $KafkaCaCertificatePath,
    $KafkaAccessCertificatePath,
    $KafkaAccessKeyPath
)

foreach ($certificateFile in $certificateFiles) {
    if (-not (Test-Path -LiteralPath $certificateFile -PathType Leaf)) {
        throw "Required Aiven Kafka certificate file was not found: $certificateFile"
    }
}

$mysqlSecurePassword = Read-Host "Aiven MySQL password" -AsSecureString
$redisSecurePassword = Read-Host "Aiven Redis password" -AsSecureString
$mysqlPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($mysqlSecurePassword)
$redisPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($redisSecurePassword)

try {
    $env:SPRING_PROFILES_ACTIVE = "aiven"
    $env:AIVEN_MYSQL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($mysqlPasswordPointer)
    $env:AIVEN_REDIS_HOST = $RedisHost
    $env:AIVEN_REDIS_PORT = $RedisPort.ToString()
    $env:AIVEN_REDIS_USERNAME = $RedisUsername
    $env:AIVEN_REDIS_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($redisPasswordPointer)
    $env:SPRING_KAFKA_BOOTSTRAP_SERVERS = $KafkaBootstrapServers
    $env:AIVEN_KAFKA_CA_CERT = Get-Content -LiteralPath $KafkaCaCertificatePath -Raw
    $env:AIVEN_KAFKA_ACCESS_CERT = Get-Content -LiteralPath $KafkaAccessCertificatePath -Raw
    $env:AIVEN_KAFKA_ACCESS_KEY = Get-Content -LiteralPath $KafkaAccessKeyPath -Raw

    Write-Host "Starting Voucher Claim Service with Aiven MySQL, Redis, and Kafka..."
    mvn spring-boot:run
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($mysqlPasswordPointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($redisPasswordPointer)
    Remove-Item Env:AIVEN_MYSQL_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AIVEN_REDIS_HOST -ErrorAction SilentlyContinue
    Remove-Item Env:AIVEN_REDIS_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:AIVEN_REDIS_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:AIVEN_REDIS_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_KAFKA_BOOTSTRAP_SERVERS -ErrorAction SilentlyContinue
    Remove-Item Env:AIVEN_KAFKA_CA_CERT -ErrorAction SilentlyContinue
    Remove-Item Env:AIVEN_KAFKA_ACCESS_CERT -ErrorAction SilentlyContinue
    Remove-Item Env:AIVEN_KAFKA_ACCESS_KEY -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
}
