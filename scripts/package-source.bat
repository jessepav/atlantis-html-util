@echo off
pushd %~dp0..
del manual-utils-src.7z
"c:\Program Files\7-Zip\7z.exe" a manual-utils-src.7z @scripts\srclist.txt -bb -xr!.*.marks -xr!.svn
popd
