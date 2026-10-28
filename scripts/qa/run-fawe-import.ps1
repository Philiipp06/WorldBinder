param([string]$JavaHome='C:\Program Files\Java\jdk-25')
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if((Get-Location).Path -ne $workspace){throw 'Run this QA script from the repository root'}
$tools=Join-Path $workspace 'build/qa-import'
$paper=Join-Path $tools 'paper-26.3-222.jar'
$fawe=Join-Path $tools 'FastAsyncWorldEdit-Paper-2.16.1.jar'
foreach($file in @($paper,$fawe)){if(-not(Test-Path -LiteralPath $file)){throw ('Missing pinned QA dependency: '+$file)}}
if((Get-FileHash -LiteralPath $paper -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'f649771a2190214306fb0cfba22c7b2cbac5df6308b1158309593603ed07d488'){throw 'Paper hash mismatch'}
if((Get-FileHash -LiteralPath $fawe -Algorithm SHA512).Hash.ToLowerInvariant() -ne '711c7c358a5b4ec73160413a4aa08829d2cd474f75e1dc379c0ebc9b63b5b6458de8113c10bbaf3594497db558d7ae2519c7d7c37353be142a797a7eeee3ce43'){throw 'FAWE hash mismatch'}
$libraries=Join-Path $tools 'paper-libraries';New-Item -ItemType Directory -Path $libraries -Force|Out-Null
$annotations=Join-Path $libraries 'annotations-26.0.2.jar'
if(-not(Test-Path -LiteralPath $annotations)){Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar' -OutFile $annotations}
$expectedAnnotations=(Invoke-RestMethod -Uri 'https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar.sha256').Trim()
if((Get-FileHash -LiteralPath $annotations -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedAnnotations){throw 'Annotations hash mismatch'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($paper)
try{foreach($entry in $zip.Entries){if($entry.FullName.StartsWith('META-INF/libraries/') -and $entry.FullName.EndsWith('.jar')){$target=Join-Path $libraries $entry.Name;if(-not(Test-Path -LiteralPath $target)){[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target)}}}}finally{$zip.Dispose()}
$classes=Join-Path $tools 'probe-classes';New-Item -ItemType Directory -Path $classes -Force|Out-Null
$mcClasspath=Join-Path $tools 'minecraft-classpath.txt'
if(-not(Test-Path -LiteralPath $mcClasspath)){& (Join-Path $workspace 'gradlew.bat') -q writeQaCompileClasspath; if($LASTEXITCODE -ne 0){throw 'Cannot prepare QA compile dependencies'}}
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 -classpath ($libraries+'/*;'+$fawe+';'+(Get-Content -LiteralPath $mcClasspath -Raw).Trim()) -d $classes (Join-Path $PSScriptRoot 'paper-import/WorldBinderImportProbe.java')
if($LASTEXITCODE -ne 0){throw 'FAWE import probe compilation failed'}
$run=Join-Path $workspace ('build/qa-fawe/run-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds());New-Item -ItemType Directory -Path (Join-Path $run 'plugins/WorldBinderImportProbe'),(Join-Path $run 'plugins/bStats'),(Join-Path $run 'plugins/FastAsyncWorldEdit') -Force|Out-Null
$pluginMeta=Join-Path $tools 'plugin-meta';New-Item -ItemType Directory -Path $pluginMeta -Force|Out-Null
@'
name: WorldBinderImportProbe
version: 1.7.0
main: net.worldbinder.qa.WorldBinderImportProbe
api-version: '1.21'
depend: [FastAsyncWorldEdit]
'@|Set-Content -LiteralPath (Join-Path $pluginMeta 'plugin.yml') -Encoding utf8
& (Join-Path $JavaHome 'bin/jar.exe') cf (Join-Path $run 'plugins/WorldBinderImportProbe.jar') -C $classes . -C $pluginMeta plugin.yml
if($LASTEXITCODE -ne 0){throw 'FAWE import probe packaging failed'}
Copy-Item -LiteralPath $fawe -Destination (Join-Path $run 'plugins/FAWE.jar')
$fixture=Get-Content -LiteralPath (Join-Path $workspace 'build/expansion-client/expansion-fixture.json') -Raw|ConvertFrom-Json
Copy-Item -LiteralPath $fixture.schematic -Destination (Join-Path $run 'plugins/WorldBinderImportProbe/input.schem')
Copy-Item -LiteralPath (Join-Path $workspace 'build/import-client/expected.tsv') -Destination (Join-Path $run 'plugins/WorldBinderImportProbe/expected.tsv')
'enabled: false'|Set-Content -LiteralPath (Join-Path $run 'plugins/bStats/config.yml') -Encoding utf8
"enabled-components:`n  metrics: false"|Set-Content -LiteralPath (Join-Path $run 'plugins/FastAsyncWorldEdit/config.yml') -Encoding utf8
'eula=true'|Set-Content -LiteralPath (Join-Path $run 'eula.txt') -Encoding ascii
@'
server-ip=127.0.0.1
server-port=0
online-mode=false
enable-rcon=false
enable-query=false
view-distance=2
simulation-distance=2
spawn-protection=0
level-name=worldbinder-import-qa
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:desert"}
generate-structures=false
sync-chunk-writes=true
'@|Set-Content -LiteralPath (Join-Path $run 'server.properties') -Encoding ascii
$run|Set-Content -LiteralPath (Join-Path $tools 'fawe-latest.txt') -Encoding utf8
Write-Output ('FAWE actual import QA: '+$run)
$server=Start-Process -FilePath (Join-Path $JavaHome 'bin/java.exe') -ArgumentList @('-Xmx2G','-Dfile.encoding=UTF-8','-jar',$paper,'--nogui') -WorkingDirectory $run -RedirectStandardOutput (Join-Path $run 'console.log') -RedirectStandardError (Join-Path $run 'stderr.log') -WindowStyle Hidden -PassThru
$deadline=[DateTimeOffset]::UtcNow.AddMinutes(3)
while(-not $server.WaitForExit(1000)){if([DateTimeOffset]::UtcNow -gt $deadline){$server.Kill($true);throw ('Owned QA server timed out; inspect '+$run)}}
$serverCode=$server.ExitCode
$result=Join-Path $run 'plugins/WorldBinderImportProbe/RESULT.txt'
if($serverCode -ne 0 -or -not(Test-Path -LiteralPath $result)){throw ('Native FAWE import did not finish successfully; inspect '+(Join-Path $run 'console.log'))}
$text=Get-Content -LiteralPath $result -Raw;Write-Output $text
if(-not$text.StartsWith('PASS')){throw 'Native FAWE import failed'}
