param()

$securePassword = Read-Host "Aiven MySQL password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $env:SPRING_PROFILES_ACTIVE = "aiven"
    $env:AIVEN_MYSQL_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    Write-Host "Starting Voucher Claim Service with the Aiven MySQL profile..."
    mvn spring-boot:run
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    Remove-Item Env:AIVEN_MYSQL_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
}
