param([switch]$NoGui)

$ErrorActionPreference = 'Stop'
$SecretBase64 = 'qgNyfcOabgYHkbUoKnQjyFSuGQJww3V71Rux1KQwqlE='
$Alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'

function Convert-ToBase36([long]$Value) {
    $chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    if ($Value -eq 0) { return '0' }
    $result = ''
    while ($Value -gt 0) {
        $result = $chars[[int]($Value % 36)] + $result
        $Value = [math]::Floor($Value / 36)
    }
    return $result
}

function Convert-ToBase32([byte[]]$Bytes) {
    $result = [System.Text.StringBuilder]::new()
    $buffer = 0
    $bits = 0
    foreach ($byte in $Bytes) {
        $buffer = ($buffer -shl 8) -bor $byte
        $bits += 8
        while ($bits -ge 5) {
            [void]$result.Append($Alphabet[($buffer -shr ($bits - 5)) -band 31])
            $bits -= 5
        }
    }
    if ($bits -gt 0) { [void]$result.Append($Alphabet[($buffer -shl (5 - $bits)) -band 31]) }
    return $result.ToString()
}

function New-TeamleiterKey {
    $now = [DateTimeOffset]::UtcNow
    $second = [long][math]::Floor($now.ToUnixTimeMilliseconds() / 1000)
    $payload = "TL1:$second"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Convert]::FromBase64String($SecretBase64))
    try { $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)) } finally { $hmac.Dispose() }
    $signature = (Convert-ToBase32 $hash).Substring(0, 12)
    [pscustomobject]@{
        Key = "TL1-$(Convert-ToBase36 $second)-$signature"
        Created = $now.ToLocalTime()
        Expires = $now.AddHours(9).ToLocalTime()
    }
}

if ($NoGui) {
    $value = New-TeamleiterKey
    $value | Format-List Key, Created, Expires
    exit
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$form = [System.Windows.Forms.Form]@{
    Text = 'AV-Erfassung – Teamleiter-Keygenerator'
    StartPosition = 'CenterScreen'
    Size = [Drawing.Size]::new(620, 310)
    FormBorderStyle = 'FixedDialog'
    MaximizeBox = $false
    BackColor = [Drawing.Color]::FromArgb(255, 204, 0)
    Font = [Drawing.Font]::new('Segoe UI', 10)
}
$title = [System.Windows.Forms.Label]@{
    Text = 'TEAMLEITER-SCHLÜSSEL'
    Location = [Drawing.Point]::new(25, 20)
    Size = [Drawing.Size]::new(550, 35)
    Font = [Drawing.Font]::new('Segoe UI', 18, [Drawing.FontStyle]::Bold)
    ForeColor = [Drawing.Color]::FromArgb(212, 5, 17)
    TextAlign = 'MiddleCenter'
}
$keyBox = [System.Windows.Forms.TextBox]@{
    Location = [Drawing.Point]::new(35, 78)
    Size = [Drawing.Size]::new(530, 35)
    ReadOnly = $true
    TextAlign = 'Center'
    Font = [Drawing.Font]::new('Consolas', 15, [Drawing.FontStyle]::Bold)
}
$expiry = [System.Windows.Forms.Label]@{
    Location = [Drawing.Point]::new(35, 122)
    Size = [Drawing.Size]::new(530, 28)
    TextAlign = 'MiddleCenter'
}
$generate = [System.Windows.Forms.Button]@{
    Text = 'NEUEN KEY ERSTELLEN'
    Location = [Drawing.Point]::new(35, 168)
    Size = [Drawing.Size]::new(255, 52)
    BackColor = [Drawing.Color]::FromArgb(212, 5, 17)
    ForeColor = [Drawing.Color]::White
    FlatStyle = 'Flat'
    Font = [Drawing.Font]::new('Segoe UI', 11, [Drawing.FontStyle]::Bold)
}
$copy = [System.Windows.Forms.Button]@{
    Text = 'KEY KOPIEREN'
    Location = [Drawing.Point]::new(310, 168)
    Size = [Drawing.Size]::new(255, 52)
    BackColor = [Drawing.Color]::White
    ForeColor = [Drawing.Color]::FromArgb(212, 5, 17)
    FlatStyle = 'Flat'
    Font = [Drawing.Font]::new('Segoe UI', 11, [Drawing.FontStyle]::Bold)
}

$setKey = {
    $value = New-TeamleiterKey
    $keyBox.Text = $value.Key
    $expiry.Text = "Gültig bis: $($value.Expires.ToString('dd.MM.yyyy HH:mm')) Uhr (9 Stunden)"
}
$generate.Add_Click($setKey)
$copy.Add_Click({
    if ($keyBox.Text) {
        [System.Windows.Forms.Clipboard]::SetText($keyBox.Text)
        $copy.Text = 'KOPIERT'
    }
})
$form.Controls.AddRange(@($title, $keyBox, $expiry, $generate, $copy))
& $setKey
[void]$form.ShowDialog()
