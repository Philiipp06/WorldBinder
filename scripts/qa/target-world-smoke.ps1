param([Parameter(Mandatory=$true)][string]$Version,[Parameter(Mandatory=$true)][string]$Java,[ValidateSet('export-','legacy-mappings-','multi-')][string]$FixturePrefix='export-')
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$qaRoot = Join-Path $workspace 'build/target-smoke'
$output = Join-Path $qaRoot ($Version + '/' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $output -Force | Out-Null
$latest = Get-ChildItem (Join-Path $workspace 'build/regression-runtime') -Directory | Where-Object {
    (Test-Path -LiteralPath (Join-Path $_.FullName 'RESULT.txt')) -and (Test-Path -LiteralPath (Join-Path $_.FullName ($FixturePrefix + $Version + '/level.dat')))
} | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (!$latest) { throw 'No completed regression export fixture found; run regressionTest first' }
$source = Join-Path $latest.FullName ($FixturePrefix + $Version)
if (!(Test-Path -LiteralPath $source)) { throw "Run regressionTest first; missing fixture $source" }
Copy-Item -LiteralPath $source -Destination (Join-Path $output 'world') -Recurse
$downloads = Join-Path $qaRoot 'downloads'; New-Item -ItemType Directory -Path $downloads -Force | Out-Null
$jar = Join-Path $downloads ('minecraft-' + $Version + '-server.jar')
if (!(Test-Path -LiteralPath $jar)) {
    $manifest = Invoke-RestMethod 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    $entry = $manifest.versions | Where-Object id -eq $Version | Select-Object -First 1
    if (!$entry) { throw "Unknown official Minecraft version $Version" }
    $details = Invoke-RestMethod $entry.url
    Invoke-WebRequest $details.downloads.server.url -OutFile $jar
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA1).Hash.ToLowerInvariant() -ne $details.downloads.server.sha1) { throw 'Official server SHA-1 mismatch' }
}
Set-Content -LiteralPath (Join-Path $output 'eula.txt') -Value 'eula=true'
@'
level-name=world
server-ip=127.0.0.1
server-port=0
online-mode=false
view-distance=2
simulation-distance=2
spawn-protection=0
enable-status=false
use-native-transport=false
max-tick-time=60000
generate-structures=false
'@ | Set-Content -LiteralPath (Join-Path $output 'server.properties')
$start = [Diagnostics.ProcessStartInfo]::new($Java)
$start.WorkingDirectory = $output; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
$start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
$start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
foreach ($arg in @('-Xmx1536M','-jar',$jar,'nogui')) { $start.ArgumentList.Add($arg) }
$process = [Diagnostics.Process]::Start($start)
$stderr = $process.StandardError.ReadToEndAsync()
$lines = [Collections.Generic.List[string]]::new()
$deadline = [DateTime]::UtcNow.AddMinutes(4); $readyAt = $null; $stopped = $false; $delayedProbe = $false
$lineTask = $process.StandardOutput.ReadLineAsync()
try {
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($lineTask.Wait(100)) {
            $line = $lineTask.Result
            if ($null -eq $line) { break }
            $lines.Add($line)
            if ($line -match 'Done \(' -and !$readyAt) {
                $readyAt = [DateTime]::UtcNow
                $countField = if ($Version.StartsWith('26.')) { 'count:3' } else { 'Count:3b' }
                $process.StandardInput.WriteLine('forceload add 0 0')
                $process.StandardInput.WriteLine('execute if block 1 64 1 minecraft:stone run say WORLDBINDER_QA_BLOCK_OK')
                if ($Version -ne '1.13.2') { $process.StandardInput.WriteLine('execute if data block 2 64 2 Items[{Slot:5b,id:"minecraft:diamond",' + $countField + '}] run say WORLDBINDER_QA_ITEMS_OK') }
                if ($Version -ne '1.13.2') { $process.StandardInput.WriteLine('execute unless data block 3 64 3 Items[0] run say WORLDBINDER_QA_EMPTY_OK') }
                if ($Version.StartsWith('26.') -or $Version -eq '1.20.4') {
                    # Vanilla's seed-dependent biome zoom may select either neighbouring received quart sample.
                    # Probe inside the recorded chunk and require one of its actual original palette entries.
                    $process.StandardInput.WriteLine('execute if biome 6 66 6 minecraft:forest run say WORLDBINDER_QA_BIOME_OK')
                    $process.StandardInput.WriteLine('execute if biome 6 66 6 minecraft:taiga run say WORLDBINDER_QA_BIOME_OK')
                }
                $process.StandardInput.WriteLine('data get block 2 64 2 Items')
                if ($FixturePrefix -eq 'legacy-mappings-') {
                    $sign = if ($Version -eq '1.13.2') { 'minecraft:sign' } else { 'minecraft:oak_sign' }
                    $cauldron = if ($Version -eq '1.20.4') { 'minecraft:water_cauldron[level=2]' } else { 'minecraft:cauldron[level=2]' }
                    $process.StandardInput.WriteLine('execute if block 4 64 4 ' + $sign + ' run say WORLDBINDER_QA_SIGN_OK')
                    $process.StandardInput.WriteLine('execute if block 6 64 4 ' + $cauldron + ' run say WORLDBINDER_QA_CAULDRON_OK')
                    $process.StandardInput.WriteLine('data get block 4 64 4')
                }
                if ($FixturePrefix -eq 'multi-') {
                    $process.StandardInput.WriteLine('execute in minecraft:the_nether run forceload add 0 0')
                    $process.StandardInput.WriteLine('execute in minecraft:the_nether if block 1 64 1 minecraft:gold_block run say WORLDBINDER_QA_NETHER_OK')
                }
                $process.StandardInput.Flush()
            }
            $lineTask = $process.StandardOutput.ReadLineAsync()
        }
        if ($readyAt -and !$delayedProbe -and ([DateTime]::UtcNow - $readyAt).TotalSeconds -gt 2) {
            if ($FixturePrefix -eq 'multi-') { $process.StandardInput.WriteLine('execute in minecraft:the_nether if block 1 64 1 minecraft:gold_block run say WORLDBINDER_QA_NETHER_OK') }
            $process.StandardInput.Flush(); $delayedProbe = $true
        }
        if ($readyAt -and !$stopped -and ([DateTime]::UtcNow - $readyAt).TotalSeconds -gt 5) {
            $process.StandardInput.WriteLine('save-all flush'); $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush(); $stopped = $true
        }
        if ($process.HasExited -and $lineTask.IsCompleted -and $null -eq $lineTask.Result) { break }
    }
    if (!$process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush(); if (!$process.WaitForExit(20000)) { $process.Kill($true) } }
} finally {
    if (!$process.HasExited) { $process.Kill($true); $process.WaitForExit() }
    $lines.Add($process.StandardOutput.ReadToEnd()); $lines.Add($stderr.GetAwaiter().GetResult())
    [IO.File]::WriteAllLines((Join-Path $output 'console.log'),$lines)
}
$log = $lines -join "`n"
$expected = @('WORLDBINDER_QA_BLOCK_OK','WORLDBINDER_QA_ITEMS_OK')
if ($Version -ne '1.13.2') { $expected += 'WORLDBINDER_QA_EMPTY_OK' }
if ($Version.StartsWith('26.') -or $Version -eq '1.20.4') { $expected += 'WORLDBINDER_QA_BIOME_OK' }
if ($FixturePrefix -eq 'legacy-mappings-') { $expected += @('WORLDBINDER_QA_SIGN_OK','WORLDBINDER_QA_CAULDRON_OK','WorldBinder QA') }
if ($FixturePrefix -eq 'multi-') { $expected += 'WORLDBINDER_QA_NETHER_OK' }
# A parser error may echo the command and its marker. Only successful say output is evidence.
$missing = @($expected | Where-Object {
    $check = $_
    if ($_ -eq 'WorldBinder QA') { !$log.Contains('has the following block data:') -or !$log.Contains('WorldBinder QA') }
    elseif ($_ -eq 'WORLDBINDER_QA_ITEMS_OK' -and $Version -eq '1.13.2') {
        !(@($lines | Where-Object { $_ -match '2, 64, 2 has the following block data:' -and $_ -match 'Slot: 5b, id: "minecraft:diamond", Count: 3b' }).Count)
    } else { !(@($lines | Where-Object { $_ -match ('\[Server\] ' + [regex]::Escape($check) + '\s*$') }).Count) }
})
$formatErrors = @($lines | Where-Object { $_ -match 'WorldGenSettings:|Couldn.t load protochunk|Could not load aux values|Recoverable errors when loading|Failed to parse saved data|Unknown registry|Error loading chunk' })
$result = [ordered]@{ version=$Version; fixture=$source; opened=[bool]$readyAt; exitCode=$process.ExitCode; missingChecks=$missing; formatErrors=$formatErrors; evidence=$output; limitations='Actual dedicated-server load and console content checks. No visual/client acceptance, map rendering, player login or light-engine behavior claim.' }
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'RESULT.json')
$result | ConvertTo-Json -Depth 5
if ($missing.Count -gt 0 -or $formatErrors.Count -gt 0 -or !$readyAt -or $process.ExitCode -ne 0) { throw 'Minecraft target-world smoke test failed; see console.log' }
