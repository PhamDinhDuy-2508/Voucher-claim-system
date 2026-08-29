param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$MerchantId = "1000000000000001"
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd('/')
$client = [System.Net.Http.HttpClient]::new()

function Invoke-JsonPost([string]$url, [object]$body, [hashtable]$headers = @{}) {
    $content = [System.Net.Http.StringContent]::new(
        ($body | ConvertTo-Json -Depth 10),
        [System.Text.Encoding]::UTF8,
        "application/json")
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post, $url)
    $request.Content = $content
    foreach ($header in $headers.GetEnumerator()) {
        $request.Headers.TryAddWithoutValidation($header.Key, $header.Value) | Out-Null
    }
    return $client.SendAsync($request).Result
}

# Step 1: create a campaign with exactly seven physical voucher slots.
$now = [DateTime]::UtcNow
$createBody = @{
    name = "Concurrent claim test - 7 slots"
    discountType = "PERCENTAGE"
    discountValue = 10
    totalQuantity = 7
    priorityOrder = "SCORE_DESC_THEN_REQUEST_MEMBER_DESC"
    startAt = $now.AddMinutes(-1).ToString("o")
    endAt = $now.AddHours(1).ToString("o")
    voucherExpiresAt = $now.AddHours(2).ToString("o")
}
$createResponse = Invoke-JsonPost "$base/api/v1/campaigns" $createBody @{
    "X-Merchant-Id" = $MerchantId
    "Idempotency-Key" = [Guid]::NewGuid().ToString()
}
$createJson = $createResponse.Content.ReadAsStringAsync().Result | ConvertFrom-Json
$campaignId = $createJson.campaignId
if ([string]::IsNullOrWhiteSpace($campaignId)) {
    throw "Campaign creation failed: $($createResponse.StatusCode)"
}
Write-Host "Step 1: campaignId=$campaignId status=$($createResponse.StatusCode)"

# Step 2: enqueue durable activation and wait until all seven slots are materialized.
$activateBody = @{ campaignId = $campaignId }
$activateResponse = Invoke-JsonPost "$base/api/v1/campaigns/activate" $activateBody @{
    "X-Merchant-Id" = $MerchantId
}
Write-Host "Step 2: activation status=$($activateResponse.StatusCode)"

$deadline = [DateTime]::UtcNow.AddSeconds(30)
do {
    Start-Sleep -Milliseconds 200
    $availability = Invoke-RestMethod "$base/api/v1/campaigns/status?campaignId=$campaignId"
} while ($availability.status -ne "ACTIVE" -and [DateTime]::UtcNow -lt $deadline)
if ($availability.status -ne "ACTIVE") {
    throw "Campaign did not become ACTIVE within 30 seconds (status=$($availability.status))"
}
Write-Host "Campaign is ACTIVE; sending ten claims concurrently."

# Step 3: issue ten HTTP requests before reading any result, so they share one burst.
$claimTasks = foreach ($number in 1..10) {
    $userId = "200000000000{0:D4}" -f $number
    $body = @{ userId = $userId; campaignId = $campaignId } | ConvertTo-Json
    $content = [System.Net.Http.StringContent]::new(
        $body, [System.Text.Encoding]::UTF8, "application/json")
    [PSCustomObject]@{
        userId = $userId
        task = $client.PostAsync("$base/api/v1/claims", $content)
    }
}
[System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]@($claimTasks.task))

$operations = foreach ($claimTask in $claimTasks) {
    $response = $claimTask.task.Result
    $body = $response.Content.ReadAsStringAsync().Result
    [PSCustomObject]@{
        userId = $claimTask.userId
        httpStatus = [int]$response.StatusCode
        body = if ([string]::IsNullOrWhiteSpace($body)) { $null } else { $body | ConvertFrom-Json }
    }
}

# Poll only asynchronous operations. A 409 is the expected fast pre-check rejection.
foreach ($operation in $operations) {
    if ($operation.httpStatus -ne 202 -or $null -eq $operation.body.requestId) {
        continue
    }
    $statusDeadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 200
        $operation.body = Invoke-RestMethod "$base/api/v1/claims/status?requestId=$($operation.body.requestId)"
    } while ($operation.body.status -notin @("SUCCEEDED", "REJECTED") -and
             [DateTime]::UtcNow -lt $statusDeadline)
}

$operations | Select-Object userId, httpStatus, @{n="status";e={$_.body.status}},
    @{n="result";e={$_.body.result}}, @{n="requestId";e={$_.body.requestId}} |
    Format-Table -AutoSize

$succeeded = @($operations | Where-Object { $_.body.status -eq "SUCCEEDED" }).Count
$rejected = @($operations | Where-Object {
    $_.httpStatus -eq 409 -or $_.body.result -eq "SOLD_OUT"
}).Count
if ($succeeded -ne 7 -or $rejected -ne 3) {
    throw "Expected 7 successful claims and 3 sold-out requests; got success=$succeeded rejected=$rejected"
}
Write-Host "PASS: 7 vouchers were issued and 3 requests were rejected as SOLD_OUT."
$client.Dispose()
