/**
 * @aegis-contract
 * @claim Foreground Window Focus and Elevation Detection Guard
 * @true Tracks active foreground HWND/Process and identifies UIPI privilege elevation blocks
 * @false Never permits input injection when foreground target is lost
 */

using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace PhoneGamepad.Receiver.Safety;

public class FocusGuard
{
    public string? TargetProcessName { get; set; }
    public bool AnyWindowMode { get; set; } = false;

    public bool IsTargetInFocus(out string currentForegroundProcess, out bool isElevationBlocked)
    {
        currentForegroundProcess = "Unknown";
        isElevationBlocked = false;

        if (AnyWindowMode)
            return true;

        if (string.IsNullOrWhiteSpace(TargetProcessName))
            return false;

        IntPtr hwnd = GetForegroundWindow();
        if (hwnd == IntPtr.Zero)
            return false;

        GetWindowThreadProcessId(hwnd, out uint processId);
        if (processId == 0)
            return false;

        try
        {
            using var proc = Process.GetProcessById((int)processId);
            currentForegroundProcess = proc.ProcessName;

            bool matches = string.Equals(proc.ProcessName, TargetProcessName, StringComparison.OrdinalIgnoreCase);
            if (matches)
            {
                // Test for UIPI Elevation Block
                isElevationBlocked = CheckUipiMismatch(processId);
                return !isElevationBlocked;
            }

            return false;
        }
        catch
        {
            // Access denied on elevated process
            isElevationBlocked = true;
            return false;
        }
    }

    private static bool CheckUipiMismatch(uint targetPid)
    {
        IntPtr hProcess = OpenProcess(0x1000 /* PROCESS_QUERY_LIMITED_INFORMATION */, false, targetPid);
        if (hProcess == IntPtr.Zero)
        {
            // If receiver cannot query the game process, the game is running elevated (UIPI)
            return Marshal.GetLastWin32Error() == 5; // ERROR_ACCESS_DENIED
        }

        CloseHandle(hProcess);
        return false;
    }

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr OpenProcess(uint processAccess, bool bInheritHandle, uint processId);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr hObject);
}
