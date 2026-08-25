#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>

static void dirname_of_exe(char *out, DWORD size) {
    GetModuleFileNameA(NULL, out, size);
    char *p = strrchr(out, '\\');
    if (p) *p = '\0';
}

int WINAPI WinMain(HINSTANCE hInst, HINSTANCE hPrev, LPSTR cmdLine, int show) {
    (void)hInst; (void)hPrev; (void)show;
    char dir[MAX_PATH];
    dirname_of_exe(dir, MAX_PATH);

    char command[8192];
    snprintf(command, sizeof(command),
        "javaw.exe -cp \"%s\\lib\\*\" com.shoshiluna.desktop.MainKt %s",
        dir, cmdLine ? cmdLine : "");

    STARTUPINFOA si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    if (!CreateProcessA(NULL, command, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxA(NULL,
            "SHOSHI LUNA DESKTOP requires Java 17 or newer in PATH.\nInstall a 64-bit JDK/JRE and launch again.",
            "SHOSHI LUNA DESKTOP", MB_OK | MB_ICONERROR);
        return 2;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
