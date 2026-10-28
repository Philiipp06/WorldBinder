param(
    [Parameter(Mandatory=$true)][string]$Artifact,
    [string[]]$Versions=@('26.1','26.1.1','26.1.2','26.2','26.3'),
    [string[]]$Providers=@('flashback','replaymod'),
    [string]$EvidenceDirectory='.qa-evidence/replay-matrix'
)
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$artifactPath=[IO.Path]::GetFullPath((Join-Path $workspace $Artifact))
if(!$artifactPath.StartsWith($workspace+[IO.Path]::DirectorySeparatorChar)){throw 'Use a frozen artifact inside this checkout'}
$sha=(Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
$inventory=Get-Content -LiteralPath (Join-Path $workspace 'build/qa-replay-downloads/inventory.json') -Raw | ConvertFrom-Json
$profiles=@{
    '26.1'=@('0.145.1+26.1','18.0.0');'26.1.1'=@('0.145.4+26.1.1','18.0.2');'26.1.2'=@('0.155.3+26.1.2','18.0.2');'26.2'=@('0.161.0+26.2','20.0.3');'26.3'=@('0.160.7+26.3','21.0.0-beta.1')
}
$evidence=[IO.Path]::GetFullPath((Join-Path $workspace $EvidenceDirectory))
if(!$evidence.StartsWith($workspace+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Keep QA evidence inside the checkout'}
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$results=[Collections.Generic.List[object]]::new()
if(Test-Path -LiteralPath (Join-Path $evidence 'RESULT.json')){
    foreach($entry in @(Get-Content -LiteralPath (Join-Path $evidence 'RESULT.json') -Raw | ConvertFrom-Json)){
        if($entry.artifactSHA256 -eq $sha -and !($Versions -contains $entry.minecraft -and $Providers -contains $entry.provider)){$results.Add($entry)}
    }
}
Push-Location $workspace
try{
foreach($minecraft in $Versions){
    if(!$profiles.ContainsKey($minecraft)){throw "No QA profile for $minecraft"}
    foreach($provider in $Providers){
        $release=$inventory | Where-Object { $_.project -eq $provider -and $_.minecraft -eq $minecraft } | Select-Object -First 1
        if(!$release){throw "Run download-replay-tools.mjs first: $provider / $minecraft"}
        $upstream=Join-Path $workspace $release.file
        if((Get-FileHash -LiteralPath $upstream -Algorithm SHA512).Hash.ToLowerInvariant() -ne $release.sha512){throw 'Replay tool checksum mismatch'}
        $attempt=Join-Path $evidence ($minecraft+'-'+$provider+'-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
        New-Item -ItemType Directory -Path $attempt | Out-Null
        $log=Join-Path $attempt 'combined.log';$recordLog=Join-Path $attempt 'record.log';$resumeLog=Join-Path $attempt 'resume.log'
        $profileArgs=@("-PcompatibilityJar=$artifactPath","-Pminecraft_version=$minecraft","-Pfabric_version=$($profiles[$minecraft][0])","-Pmodmenu_version=$($profiles[$minecraft][1])","-PreplayModJar=$upstream","-PreplayProvider=$provider",'--no-parallel','--console=plain')
        & (Join-Path $workspace 'gradlew.bat') :runReplayCaptureTest @profileArgs *> $recordLog
        $exitCode=$LASTEXITCODE;$text=Get-Content -LiteralPath $recordLog -Raw
        $recordPassed=$exitCode -eq 0 -and $text.Contains("WORLDBINDER_REPLAY_PASS $provider record") -and $text.Contains($sha)
        $resumePassed=$false
        if($recordPassed){
            & (Join-Path $workspace 'gradlew.bat') :runReplayCaptureRestartTest @profileArgs *> $resumeLog
            $exitCode=$LASTEXITCODE;$resumeText=Get-Content -LiteralPath $resumeLog -Raw;$text+="`n"+$resumeText
            $resumePassed=$exitCode -eq 0 -and $resumeText.Contains("WORLDBINDER_REPLAY_PASS $provider resume") -and $resumeText.Contains($sha)
        }
        $text | Set-Content -LiteralPath $log
        $passed=$recordPassed -and $resumePassed
        if((Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha){throw 'Frozen production artifact changed during compatibility testing'}
        $results.Add([ordered]@{minecraft=$minecraft;provider=$provider;upstream=$release.version;artifactSHA256=$sha;passed=$passed;recordPassed=$recordPassed;resumePassed=$resumePassed;exitCode=$exitCode;log=$log})
        Write-Output "$minecraft $provider $($release.version): $passed"
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'RESULT.json')
    }
}
}finally{Pop-Location}
if(@($results | Where-Object {!$_.passed}).Count){throw 'Replay matrix contains failures; inspect the individual logs'}
