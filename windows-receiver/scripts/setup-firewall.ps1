<#
.SYNOPSIS
    Configures Windows Defender Firewall inbound rule for Phone-as-Gamepad UDP listener (Port 47789).
.DESCRIPTION
    Requires Administrative elevation.
#>
param(
    [int]$Port = 47789,
    [string]$RuleName = "Phone-as-Gamepad Receiver (UDP-In 47789)"
)

$existingRule = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue

if ($existingRule) {
    Write-Host "[Firewall] Rule '$RuleName' already exists." -ForegroundColor Green
} else {
    try {
        New-NetFirewallRule -DisplayName $RuleName `
                            -Direction Inbound `
                            -LocalPort $Port `
                            -Protocol UDP `
                            -Action Allow `
                            -Description "Allows incoming 120 Hz UDP control packets for Phone-as-Gamepad." `
                            -ErrorAction Stop
        Write-Host "[Firewall] Successfully created inbound firewall rule for UDP port $Port." -ForegroundColor Green
    } catch {
        Write-Warning "[Firewall] Failed to create firewall rule. Ensure this script is run as Administrator."
        Write-Warning $_.Exception.Message
    }
}
