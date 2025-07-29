@echo off
echo GitHub grass planting started (Jan 1, 2024 ~ Jul 29, 2025)
echo ==================================================

REM 2024년 심기
echo Processing 2024...

REM 1월
for /l %%d in (1,1,31) do call :commit_day 2024 01 %%d

REM 2월 (윤년)
for /l %%d in (1,1,29) do call :commit_day 2024 02 %%d

REM 3월
for /l %%d in (1,1,31) do call :commit_day 2024 03 %%d

REM 4월
for /l %%d in (1,1,30) do call :commit_day 2024 04 %%d

REM 5월
for /l %%d in (1,1,31) do call :commit_day 2024 05 %%d

REM 6월
for /l %%d in (1,1,30) do call :commit_day 2024 06 %%d

REM 7월
for /l %%d in (1,1,31) do call :commit_day 2024 07 %%d

REM 8월
for /l %%d in (1,1,31) do call :commit_day 2024 08 %%d

REM 9월
for /l %%d in (1,1,30) do call :commit_day 2024 09 %%d

REM 10월
for /l %%d in (1,1,31) do call :commit_day 2024 10 %%d

REM 11월
for /l %%d in (1,1,30) do call :commit_day 2024 11 %%d

REM 12월
for /l %%d in (1,1,31) do call :commit_day 2024 12 %%d

REM 2025년 심기
echo Processing 2025...

REM 1월
for /l %%d in (1,1,31) do call :commit_day 2025 01 %%d

REM 2월
for /l %%d in (1,1,28) do call :commit_day 2025 02 %%d

REM 3월
for /l %%d in (1,1,31) do call :commit_day 2025 03 %%d

REM 4월
for /l %%d in (1,1,30) do call :commit_day 2025 04 %%d

REM 5월
for /l %%d in (1,1,31) do call :commit_day 2025 05 %%d

REM 6월
for /l %%d in (1,1,30) do call :commit_day 2025 06 %%d

REM 7월 (29일까지만)
for /l %%d in (1,1,29) do call :commit_day 2025 07 %%d

echo ==================================================
echo Grass planting completed!
echo Now run 'git push' to upload to GitHub!
echo.
echo Check total commits:
echo git log --oneline | find /c /v ""
echo.
echo Check recent commits:
echo git log --oneline -10

goto :eof

:commit_day
setlocal enabledelayedexpansion
set year=%1
set month=%2
set day=%3

REM 날짜 형식 맞추기 (01, 02 형태)
if %day% LSS 10 set day=0%day%

REM 70% 확률로 커밋 (0-9 중 0-6이면 커밋)
set /a rand=%RANDOM% %% 10
if %rand% GEQ 7 (
    echo Skip: %year%-%month%-%day% - rest day
    goto :eof
)

REM 랜덤 커밋 개수 (1-5개)
set /a commits=%RANDOM% %% 5 + 1

for /l %%i in (1,1,%commits%) do (
    REM 랜덤 시간 생성
    set /a hour=%RANDOM% %% 24
    set /a minute=%RANDOM% %% 60
    
    REM 시간 형식 맞추기
    if !hour! LSS 10 set hour=0!hour!
    if !minute! LSS 10 set minute=0!minute!
    
    set commit_time=%year%-%month%-%day%T!hour!:!minute!:00
    
    REM 환경변수 설정하고 커밋
    set GIT_AUTHOR_DATE=!commit_time!
    set GIT_COMMITTER_DATE=!commit_time!
    git commit --allow-empty -m "Auto commit %year%-%month%-%day% #%%i"
)

echo Done: %year%-%month%-%day% - %commits% commits
endlocal
goto :eof