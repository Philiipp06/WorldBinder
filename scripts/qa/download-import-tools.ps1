param()
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if((Get-Location).Path -ne $workspace){throw 'Run from the repository root'}
$tools=Join-Path $workspace 'build/qa-import'
foreach($entry in (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'import-tools.json') -Raw|ConvertFrom-Json)){
    $directory=if($entry.runtime -eq 'fabric'){Join-Path $tools 'mods'}else{$tools}
    New-Item -ItemType Directory -Path $directory -Force|Out-Null
    $target=Join-Path $directory $entry.file
    if(-not(Test-Path -LiteralPath $target)){Invoke-WebRequest -Uri $entry.url -OutFile $target}
    $actual=(Get-FileHash -LiteralPath $target -Algorithm $entry.algorithm).Hash.ToLowerInvariant()
    if($actual -ne $entry.hash){throw ('Pinned QA dependency checksum mismatch: '+$entry.file)}
    Write-Output ('Verified '+$entry.file+' '+$actual)
}
