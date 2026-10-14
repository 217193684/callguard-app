# Starts the CallGuard backend on your Windows PC so phones on the same Wi-Fi can reach it.
# Run from this folder in PowerShell:   .\run_server.ps1
# If scripts are blocked:               powershell -ExecutionPolicy Bypass -File .\run_server.ps1

if (-not (Test-Path ".venv")) {
    python -m venv .venv
    .\.venv\Scripts\python -m pip install --upgrade pip
    .\.venv\Scripts\pip install -r requirements.txt
}

# Change this to your own long random string before sharing the server with anyone.
$env:CALLGUARD_ADMIN_KEY = "change-me-to-a-long-random-string"

Write-Host ""
Write-Host "Your PC's Wi-Fi address (put http://THIS-ADDRESS:8000 in the app's Settings):"
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
    ForEach-Object { "  http://$($_.IPAddress):8000" }
Write-Host ""

.\.venv\Scripts\uvicorn app:app --host 0.0.0.0 --port 8000
