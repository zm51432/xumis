<#
 ============================================================================
  shine星心 外贸独立站 · Windows 一键安装/启动脚本 (PowerShell)
 ----------------------------------------------------------------------------
  用法（在 PowerShell 中）：
    .\install.ps1                # 安装依赖 + 构建（不启动）
    .\install.ps1 -Start         # 安装依赖 + 构建 + 启动
    .\install.ps1 -Start -Port 9090
    .\install.ps1 -Start -SkipBuild   # 已有 jar 时跳过构建
    .\install.ps1 -Stop          # 停止正在运行的实例

  数据库：默认使用 MySQL（唯一数据源），需本机已启动 MySQL 8；
    可用环境变量指定：DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
    无 MySQL 时临时预览： .\install.ps1 -Start -SpringProfile h2

  设计原则：
    · JDK / Maven 只下载解压到本项目 .tools\ 目录，不需要管理员权限、不改系统环境变量
    · 上传文件落在项目目录（uploads\），删除目录即卸载
 ============================================================================
#>
param(
    [switch]$Start,
    [switch]$Stop,
    [switch]$SkipBuild,
    [int]$Port = 8080,
    [string]$SpringProfile = "dev"
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ---------- 路径 ----------
$Root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$ToolsDir  = Join-Path $Root ".tools"
# 仓库按 server/ + android/ 分类后，JDK/Maven 缓存可能仍留在仓库根的 .tools
if (-not (Test-Path $ToolsDir)) {
    $RootTools = Join-Path (Split-Path -Parent $Root) ".tools"
    if (Test-Path $RootTools) { $ToolsDir = $RootTools }
}
$JdkDir    = Join-Path $ToolsDir "jdk17"
$MvnDir    = Join-Path $ToolsDir "maven"
$Runtime   = Join-Path $Root "runtime"
$Uploads   = Join-Path $Root "uploads"
$LogDir    = Join-Path $Runtime "logs"
$PidFile   = Join-Path $Runtime "app.pid"
$JarPath   = Join-Path $Root "target\shine-xumis.jar"

# ---------- 数据库（MySQL，可用环境变量覆盖）----------
$DbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }
$DbPort = if ($env:DB_PORT) { $env:DB_PORT } else { "3306" }
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "shine" }

$JdkUrl  = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
$MvnVer  = "3.9.9"
$MvnUrl  = "https://archive.apache.org/dist/maven/maven-3/$MvnVer/binaries/apache-maven-$MvnVer-bin.zip"
$MvnUrl2 = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/$MvnVer/apache-maven-$MvnVer-bin.zip"

function Write-Info($msg) { Write-Host "[信息] $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "[完成] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "[提示] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[错误] $msg" -ForegroundColor Red }

# ---------- 停止 ----------
if ($Stop) {
    if (Test-Path $PidFile) {
        $p = Get-Content $PidFile -ErrorAction SilentlyContinue
        if ($p -and (Get-Process -Id $p -ErrorAction SilentlyContinue)) {
            Stop-Process -Id $p -Force
            Write-Ok "已停止站点进程 PID=$p"
        }
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    } else {
        Write-Warn "未发现运行中的实例。"
    }
    exit 0
}

# ---------- 创建目录 ----------
foreach ($d in @($ToolsDir, $Runtime, $Uploads, $LogDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}
Write-Info "工作目录：$Root"

# ---------- 下载工具 ----------
function Download-File($url, $out) {
    Write-Info "下载：$url"
    try {
        # PowerShell 7 优先
        Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing -TimeoutSec 900
        return $true
    } catch {
        Write-Warn "下载失败：$($_.Exception.Message)"
        return $false
    }
}

function Extract-Zip($zip, $dest) {
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    if ($PSVersionTable.PSVersion.Major -ge 5) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $dest)
    } else {
        $shell = New-Object -ComObject Shell.Application
        $shell.Namespace($dest).CopyHere($shell.Namespace($zip).Items(), 16)
    }
}

# ---------- 步骤 1：JDK ----------
Write-Info "步骤 1/4：检查 Java 运行环境（需要 JDK 17+）"
$javaExe = $null
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\java.exe") }
$candidates += (Join-Path $JdkDir "bin\java.exe")
$cmdJava = Get-Command java -ErrorAction SilentlyContinue
if ($cmdJava) { $candidates += $cmdJava.Source }

foreach ($c in $candidates) {
    if ($c -and (Test-Path $c)) {
        $v = & $c "-version" 2>&1 | Select-Object -First 1
        if ($v -match 'version "(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 17) { $javaExe = $c; Write-Ok "使用 Java $major ：$c"; break }
        }
    }
}

if (-not $javaExe) {
    Write-Warn "未找到 JDK 17+，自动下载到 .tools\jdk17（仅本项目内，不影响系统）..."
    $zip = Join-Path $ToolsDir "jdk17.zip"
    if (Download-File $JdkUrl $zip) {
        Extract-Zip $zip $JdkDir
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        # Temurin 压缩包内有一层目录，需要上提
        $inner = Get-ChildItem $JdkDir -Directory | Select-Object -First 1
        if ($inner -and -not (Test-Path (Join-Path $JdkDir "bin\java.exe"))) {
            Get-ChildItem $inner.FullName | ForEach-Object {
                Move-Item $_.FullName (Join-Path $JdkDir $_.Name) -Force
            }
            Remove-Item $inner.FullName -Recurse -Force -ErrorAction SilentlyContinue
        }
        $javaExe = Join-Path $JdkDir "bin\java.exe"
        Write-Ok "JDK 17 已安装到 .tools\jdk17"
    } else {
        Write-Err "自动下载 JDK 失败。请手动安装 JDK 17+：https://adoptium.net/temurin/releases/?version=17"
        exit 1
    }
}
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)

# ---------- 步骤 2：Maven ----------
Write-Info "步骤 2/4：检查 Maven（需要 3.6+）"
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
$mvnExe = Join-Path $MvnDir "bin\mvn.cmd"
if ($mvnCmd) {
    $mvn = $mvnCmd.Source
    Write-Ok "使用系统 Maven：$mvn"
} elseif (Test-Path $mvnExe) {
    $mvn = $mvnExe
    Write-Ok "使用本地自带 Maven：$mvn"
} else {
    Write-Warn "未找到 Maven，自动下载到 .tools\maven（仅本项目内）..."
    $zip = Join-Path $ToolsDir "maven.zip"
    if ((Download-File $MvnUrl $zip) -or (Download-File $MvnUrl2 $zip)) {
        Extract-Zip $zip $MvnDir
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
        $inner = Get-ChildItem $MvnDir -Directory | Select-Object -First 1
        if ($inner -and -not (Test-Path (Join-Path $MvnDir "bin\mvn.cmd"))) {
            Get-ChildItem $inner.FullName | ForEach-Object {
                Move-Item $_.FullName (Join-Path $MvnDir $_.Name) -Force
            }
            Remove-Item $inner.FullName -Recurse -Force -ErrorAction SilentlyContinue
        }
        $mvn = Join-Path $MvnDir "bin\mvn.cmd"
        Write-Ok "Maven $MvnVer 已安装到 .tools\maven"
    } else {
        Write-Err "自动下载 Maven 失败。请手动安装 Maven 3.6+ 后重试。"
        exit 1
    }
}

# ---------- 步骤 3：构建 ----------
if ($SkipBuild -and (Test-Path $JarPath)) {
    Write-Info "步骤 3/4：跳过构建（-SkipBuild），使用已有 $JarPath"
} else {
    Write-Info "步骤 3/4：编译打包（首次会下载依赖，约 2-5 分钟）..."
    & $mvn -B -q clean package "-DskipTests"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "构建失败，请查看上方 Maven 输出。"
        exit 1
    }
    Write-Ok "构建完成：$JarPath"
}

if (-not (Test-Path $JarPath)) {
    Write-Err "未找到产物 $JarPath"
    exit 1
}

# ---------- 步骤 4：启动 ----------
if (-not $Start) {
    Write-Ok "安装与构建完成。执行 .\\install.ps1 -Start 启动站点，或双击 start.bat。"
    exit 0
}

if (Test-Path $PidFile) {
    $old = Get-Content $PidFile -ErrorAction SilentlyContinue
    if ($old -and (Get-Process -Id $old -ErrorAction SilentlyContinue)) {
        Write-Warn "检测到已运行实例 PID=$old，先停止..."
        Stop-Process -Id $old -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

# 数据库预检：站点使用 MySQL（唯一数据源），连不上时给出提示但不阻断
Write-Info "步骤 3.5/4：检查 MySQL（$DbHost`:$DbPort，库 $DbName）..."
$mysqlOk = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $conn = $tcp.BeginConnect($DbHost, [int]$DbPort, $null, $null)
    $mysqlOk = $conn.AsyncWaitHandle.WaitOne(2000) -and $tcp.Connected
    $tcp.Close()
} catch { $mysqlOk = $false }
if ($mysqlOk) {
    Write-Ok "MySQL 可连接（库不存在时会自动创建）"
} else {
    Write-Warn "连不上 MySQL（$DbHost`:$DbPort），启动后可能报错。"
    Write-Warn "  · 请先安装并启动 MySQL 8（https://dev.mysql.com/downloads/installer/）"
    Write-Warn "  · 或设置环境变量：DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD"
    Write-Warn "  · 临时用内置文件库预览： .\install.ps1 -Start -SpringProfile h2"
}

Write-Info "步骤 4/4：启动站点（profile=$SpringProfile，端口 $Port）..."
$logFile = Join-Path $LogDir "app.log"
$proc = Start-Process -FilePath $javaExe -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $logFile -RedirectStandardError (Join-Path $LogDir "app.err.log") `
    -ArgumentList @("-Dfile.encoding=UTF-8", "-jar", "`"$JarPath`"",
                    "--spring.profiles.active=$SpringProfile", "--server.port=$Port")
Set-Content -Path $PidFile -Value $proc.Id -Encoding ASCII

Write-Info "等待服务就绪..."
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
}
if (-not $ready) {
    Write-Err "启动超时，请查看日志：$logFile"
    exit 1
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Ok " shine星心 独立站已启动"
Write-Host "   前台首页   http://localhost:$Port/"
Write-Host "   后台管理   http://localhost:$Port/admin?key=admin"
Write-Host "   日志       $logFile"
Write-Host "   停止服务   .\\install.ps1 -Stop"
Write-Host "============================================================" -ForegroundColor Green
