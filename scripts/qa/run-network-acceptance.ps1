param(
    [Parameter(Mandatory=$true)][string]$Artifact,
    [string]$NeoArtifact,
    [ValidateSet('viafabric','viafabricplus','dimensions-fabric','dimensions-neoforge','gallery')]
    [string[]]$Cases=@('viafabric','viafabricplus','dimensions-fabric','dimensions-neoforge')
)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
function WorkspaceArtifact([string]$relative){
    if(!$relative){throw 'Supply the frozen loader-specific artifact'}
    $resolved=[IO.Path]::GetFullPath((Join-Path $workspace $relative))
    if(!$resolved.StartsWith($workspace+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-or !(Test-Path -LiteralPath $resolved -PathType Leaf)){throw 'Use a frozen artifact inside this checkout'}
    return $resolved
}
$fabric=WorkspaceArtifact $Artifact
$neo=if($Cases -contains 'dimensions-neoforge'){WorkspaceArtifact $NeoArtifact}else{$null}
$inventory=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'network-tools.json') -Raw | ConvertFrom-Json
$evidence=Join-Path $workspace ('.qa-evidence/network-acceptance/'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$results=[Collections.Generic.List[object]]::new()
Push-Location $workspace
try{
foreach($case in $Cases){
    $translator=$case -in @('viafabric','viafabricplus')
    $isNeo=$case -eq 'dimensions-neoforge'
    $jar=if($isNeo){$neo}else{$fabric}
    $sha=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    $serverArgs=if($translator){@('--server=1.20.4',"--client-translator=$case",'--content-checks','--minimal-ui')}elseif($case -eq 'gallery'){@('--dimensions')}else{@('--dimensions','--minimal-ui')}
    $hostLog=Join-Path $evidence "$case-host.log";$hostError=Join-Path $evidence "$case-host-errors.log"
    $fixtureHost=Start-Process -FilePath (Get-Command node).Source -ArgumentList (@('scripts/qa/proxy-network.mjs')+$serverArgs) -WorkingDirectory $workspace -WindowStyle Hidden -RedirectStandardOutput $hostLog -RedirectStandardError $hostError -PassThru
    $control=$null
    try{
        $deadline=[DateTime]::UtcNow.AddMinutes(3)
        while([DateTime]::UtcNow -lt $deadline){
            if($fixtureHost.HasExited){throw "Fixture host exited: $hostLog / $hostError"}
            $ready=Select-String -LiteralPath $hostLog -Pattern '^WORLDBINDER_PROXY_READY (.+)$' | Select-Object -Last 1
            if($ready){$control=Get-Content -LiteralPath 'build/proxy-network-runtime/control.json' -Raw | ConvertFrom-Json;if($control.root -eq $ready.Matches[0].Groups[1].Value){break};$control=$null}
            Start-Sleep -Seconds 1
        }
        if(!$control){throw "Fixture readiness timed out: $hostLog"}
        # A fresh directory keeps earlier runs and the two translators' configuration apart.
        $game='build/network-acceptance/'+(Split-Path $evidence -Leaf)+'/'+$case
        $gradleArgs=if($isNeo){@(':neoforge:runProxyNetworkTest',':neoforge:runProxyNetworkRestartTest',"-PcompatibilityNeoJar=$jar","-PproxyNeoGameDir=$game")}else{@(':runProxyNetworkTest',':runProxyNetworkRestartTest',"-PcompatibilityJar=$jar","-PproxyGameDir=$game")}
        $original=$null
        if($translator){
            $tool=$inventory | Where-Object {$_.id -eq $case} | Select-Object -First 1
            $original=Join-Path $workspace ('build/qa-network-downloads/'+$tool.file)
            if((Get-FileHash -LiteralPath $original -Algorithm $tool.algorithm).Hash.ToLowerInvariant() -ne $tool.hash){throw 'Original translator checksum mismatch; run download-network-tools.mjs'}
            $gradleArgs+="-PnetworkModJar=$original"
        }
        $log=Join-Path $evidence "$case-client.log"
        & (Join-Path $workspace 'gradlew.bat') @gradleArgs --no-parallel --console=plain *> $log
        $exitCode=$LASTEXITCODE;$text=Get-Content -LiteralPath $log -Raw
        $record=[regex]::Match($text,'WORLDBINDER_PROXY_PASS record (\d+) checks')
        $resume=[regex]::Match($text,'WORLDBINDER_PROXY_PASS resume (\d+) checks')
        $passed=$exitCode -eq 0 -and $record.Success -and $resume.Success -and $text.Contains($sha)
        if((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha){throw 'Frozen artifact changed'}
        $results.Add([ordered]@{case=$case;passed=$passed;exitCode=$exitCode;minecraft='26.3';loader=$(if($isNeo){'NeoForge'}else{'Fabric'});backendMinecraft=$control.serverMinecraft;serverSideTranslation=$control.translation;originalTranslator=$original;artifactSHA256=$sha;recordChecks=$(if($record.Success){[int]$record.Groups[1].Value}else{0});restartChecks=$(if($resume.Success){[int]$resume.Groups[1].Value}else{0});gameDirectory=$game;fixtureRoot=$control.root;log=$log})
        $results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'RESULT.json')
        Write-Output "$case : $passed ($log)"
    }finally{
        if($control){Invoke-RestMethod -Uri $control.api -Method Post -ContentType 'application/json' -Body '{"stop":true}' | Out-Null}
    }
}
}finally{Pop-Location}
if(@($results | Where-Object {!$_.passed}).Count){throw 'Network acceptance contains failed cases; inspect the preserved logs'}
