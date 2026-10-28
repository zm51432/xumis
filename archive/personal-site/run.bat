@echo off
cd /d "%~dp0"
echo ============================================
echo   Portfolio Server (Java backend)
echo   公开页: http://localhost:8080/
echo   后台  : http://localhost:8080/admin
echo   默认账号: admin / admin123
echo ============================================
javac -encoding UTF-8 PortfolioServer.java
if errorlevel 1 (
  echo 编译失败，请确认已安装 JDK 17+ 并加入 PATH。
  pause
  exit /b 1
)
java PortfolioServer
pause
