@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================================
REM   shine星心 外贸独立站 · Windows 一键启动（双击本文件即可）
REM  ---------------------------------------------------------------------------
REM   本脚本会自动完成：
REM     1) 检查/安装 JDK 17（仅装到本项目 .tools\jdk17，不影响系统）
REM     2) 检查/安装 Maven（仅装到本项目 .tools\maven）
REM     3) 编译打包  target\shine-xumis.jar
REM     4) 启动站点并打开浏览器
REM
REM   可选：在命令行中指定端口，例如  start.bat 9090
REM ============================================================================

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"

echo.
echo   shine星心 外贸独立站 - 一键部署
echo   ----------------------------------------
echo   端口: %PORT%
echo   首次运行会自动下载 JDK / Maven 到 .tools 目录，请耐心等待
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -Start -Port %PORT%
if errorlevel 1 (
  echo.
  echo   [错误] 启动失败，请查看上方输出或 runtime\logs\app.log
  pause
  exit /b 1
)

echo.
echo   正在打开浏览器...
start "" "http://localhost:%PORT%/"

echo.
echo   站点已在后台运行。关闭本窗口不会停止站点。
echo   停止服务请运行: install.ps1 -Stop   （或直接关闭 java 进程）
echo.
pause
