@echo off
for %%f in (test\*.mj) do (
    echo Kompajliram %%~nf.mj
    java -cp "bin;lib\*" rs.ac.bg.etf.pp1.Compiler test\%%~nf.mj test\%%~nf.obj >test\%%~nf.out 2>test\%%~nf.err
)
echo.
echo output files generated.
pause