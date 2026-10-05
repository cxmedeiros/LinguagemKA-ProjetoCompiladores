# Compila a Ka e executa os testes funcionais em tests/{tokens,ast,errors}.
# Uso: .\tests\run-tests.ps1 [-Filter parte_do_nome]
param([string]$Filter = "")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$out  = Join-Path $root "out"

$javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
if (-not $javac -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\javac.exe"
    if (Test-Path $candidate) { $javac = $candidate }
}
if (-not $javac) {
    Write-Host "javac nao encontrado. Instale um JDK e/ou configure JAVA_HOME." -ForegroundColor Red
    exit 2
}
$java = Join-Path (Split-Path -Parent $javac) "java.exe"

$sources = (Get-ChildItem -Recurse (Join-Path $root "Ka") -Filter *.java).FullName
& $javac -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) {
    Write-Host "Falha na compilacao." -ForegroundColor Red
    exit 2
}

function Normalize([string]$text) {
    if (-not $text) { return "" }
    return ($text -replace "`r`n", "`n").TrimEnd("`n", " ")
}

function Invoke-Ka([string]$file) {
    $stdout = [System.IO.Path]::GetTempFileName()
    $stderr = [System.IO.Path]::GetTempFileName()
    $javaArgs = "-cp `"$out`" ka.Ka `"$file`""
    $proc = Start-Process -FilePath $java -ArgumentList $javaArgs -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $result = [pscustomobject]@{
        ExitCode = $proc.ExitCode
        Stdout   = Normalize (Get-Content $stdout -Raw -Encoding UTF8)
        Stderr   = Normalize (Get-Content $stderr -Raw -Encoding UTF8)
    }
    Remove-Item $stdout, $stderr
    return $result
}

# Secao de stdout entre "=== Tokens ===" e "=== Parser ===" (ou o fim).
function Get-TokensSection([string]$stdout) {
    $lines = $stdout -split "`n"
    $section = @()
    $inside = $false
    foreach ($line in $lines) {
        if ($line -eq "=== Tokens ===") { $inside = $true; continue }
        if ($line -eq "=== Parser ===") { break }
        if ($inside) { $section += $line }
    }
    return Normalize ($section -join "`n")
}

# Tudo depois de "=== Parser ===" (AST + linha de resumo).
function Get-ParserSection([string]$stdout) {
    $lines = $stdout -split "`n"
    $index = [array]::IndexOf($lines, "=== Parser ===")
    if ($index -lt 0) { return "" }
    return Normalize (($lines[($index + 1)..($lines.Count - 1)]) -join "`n")
}

$passed = 0
$failed = @()

foreach ($kind in "tokens", "ast", "errors") {
    $dir = Join-Path $PSScriptRoot $kind
    foreach ($ka in Get-ChildItem $dir -Filter *.ka | Sort-Object Name) {
        if ($Filter -and $ka.BaseName -notlike "*$Filter*") { continue }

        $expected = Normalize (Get-Content ([IO.Path]::ChangeExtension($ka.FullName, ".expected")) -Raw -Encoding UTF8)
        $run = Invoke-Ka $ka.FullName
        $problem = $null

        switch ($kind) {
            "tokens" {
                $actual = Get-TokensSection $run.Stdout
            }
            "ast" {
                $actual = Get-ParserSection $run.Stdout
                if ($run.ExitCode -ne 0) { $problem = "exit code $($run.ExitCode), esperado 0" }
                elseif ($run.Stderr)     { $problem = "stderr nao vazio: $($run.Stderr)" }
            }
            "errors" {
                $actual = $run.Stderr
                if ($run.ExitCode -ne 65) { $problem = "exit code $($run.ExitCode), esperado 65" }
                elseif ($run.Stdout -match "=== Parser ===") { $problem = "o parser nao deveria ter produzido AST" }
            }
        }

        if (-not $problem -and $actual -ne $expected) { $problem = "saida diferente do esperado" }

        $name = "$kind/$($ka.BaseName)"
        if ($problem) {
            Write-Host "[FALHOU] $name - $problem" -ForegroundColor Red
            Write-Host "--- esperado`n$expected`n--- obtido`n$actual`n"
            $failed += $name
        } else {
            Write-Host "[OK]     $name" -ForegroundColor Green
            $passed++
        }
    }
}

Write-Host "`n$passed passaram, $($failed.Count) falharam."
if ($failed.Count -gt 0) { exit 1 }
