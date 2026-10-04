using System.Runtime.InteropServices;

namespace InputTester;

public static class SendInputTester
{
    private const uint INPUT_MOUSE = 0;
    private const uint INPUT_KEYBOARD = 1;

    private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_SCANCODE = 0x0008;

    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct INPUTUNION
    {
        [FieldOffset(0)]
        public MOUSEINPUT mi;

        [FieldOffset(0)]
        public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public INPUTUNION u;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, [In] INPUT[] pInputs, int cbSize);

    public static async Task RunInjectionTestAsync(Action<string> log)
    {
        log("Starting SendInput injection test in 3 seconds. Focus target window if desired...");
        for (int i = 3; i >= 1; i--)
        {
            log($"Injecting in {i}...");
            await Task.Delay(1000);
        }

        log("Injecting: Key 'W' Down (Scan code 0x11, KEYEVENTF_SCANCODE)...");
        SendKey(0x11, isDown: true);
        await Task.Delay(150);

        log("Injecting: Mouse Move relative (+40, -25)...");
        SendMouseMove(40, -25);
        await Task.Delay(100);

        log("Injecting: Mouse LMB Down and Up (Click)...");
        SendMouseClick();
        await Task.Delay(100);

        log("Injecting: Key 'W' Up (Scan code 0x11, KEYEVENTF_SCANCODE | KEYEVENTF_KEYUP)...");
        SendKey(0x11, isDown: false);

        log("SendInput test sequence complete!");
    }

    private static void SendKey(ushort scanCode, bool isDown)
    {
        var input = new INPUT
        {
            type = INPUT_KEYBOARD,
            u = new INPUTUNION
            {
                ki = new KEYBDINPUT
                {
                    wVk = 0,
                    wScan = scanCode,
                    dwFlags = KEYEVENTF_SCANCODE | (isDown ? 0u : KEYEVENTF_KEYUP),
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            }
        };
        SendInput(1, [input], Marshal.SizeOf<INPUT>());
    }

    private static void SendMouseMove(int dx, int dy)
    {
        var input = new INPUT
        {
            type = INPUT_MOUSE,
            u = new INPUTUNION
            {
                mi = new MOUSEINPUT
                {
                    dx = dx,
                    dy = dy,
                    mouseData = 0,
                    dwFlags = MOUSEEVENTF_MOVE,
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            }
        };
        SendInput(1, [input], Marshal.SizeOf<INPUT>());
    }

    private static void SendMouseClick()
    {
        var down = new INPUT
        {
            type = INPUT_MOUSE,
            u = new INPUTUNION
            {
                mi = new MOUSEINPUT { dwFlags = MOUSEEVENTF_LEFTDOWN }
            }
        };
        var up = new INPUT
        {
            type = INPUT_MOUSE,
            u = new INPUTUNION
            {
                mi = new MOUSEINPUT { dwFlags = MOUSEEVENTF_LEFTUP }
            }
        };
        SendInput(2, [down, up], Marshal.SizeOf<INPUT>());
    }
}
