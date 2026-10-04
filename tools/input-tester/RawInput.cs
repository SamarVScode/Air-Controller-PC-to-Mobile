using System.Runtime.InteropServices;

namespace InputTester;

public static class RawInput
{
    public const int WM_INPUT = 0x00FF;

    public const uint RID_INPUT = 0x10000003;

    public const uint RIM_TYPEMOUSE = 0;
    public const uint RIM_TYPEKEYBOARD = 1;

    public const ushort RIDEV_INPUTSINK = 0x00000100;

    public const ushort RI_KEY_MAKE = 0;
    public const ushort RI_KEY_BREAK = 1;
    public const ushort RI_KEY_E0 = 2;
    public const ushort RI_KEY_E1 = 4;

    public const ushort RI_MOUSE_LEFT_BUTTON_DOWN   = 0x0001;
    public const ushort RI_MOUSE_LEFT_BUTTON_UP     = 0x0002;
    public const ushort RI_MOUSE_RIGHT_BUTTON_DOWN  = 0x0004;
    public const ushort RI_MOUSE_RIGHT_BUTTON_UP    = 0x0008;
    public const ushort RI_MOUSE_MIDDLE_BUTTON_DOWN = 0x0010;
    public const ushort RI_MOUSE_MIDDLE_BUTTON_UP   = 0x0020;
    public const ushort RI_MOUSE_BUTTON_4_DOWN      = 0x0040;
    public const ushort RI_MOUSE_BUTTON_4_UP        = 0x0080;
    public const ushort RI_MOUSE_BUTTON_5_DOWN      = 0x0100;
    public const ushort RI_MOUSE_BUTTON_5_UP        = 0x0200;
    public const ushort RI_MOUSE_WHEEL              = 0x0400;

    [StructLayout(LayoutKind.Sequential)]
    public struct RAWINPUTDEVICE
    {
        public ushort usUsagePage;
        public ushort usUsage;
        public uint dwFlags;
        public IntPtr hwndTarget;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct RAWINPUTHEADER
    {
        public uint dwType;
        public uint dwSize;
        public IntPtr hDevice;
        public IntPtr wParam;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct RAWKEYBOARD
    {
        public ushort MakeCode;
        public ushort Flags;
        public ushort Reserved;
        public ushort VKey;
        public uint Message;
        public uint ExtraInformation;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct RAWMOUSE
    {
        public ushort usFlags;
        public ushort usButtonFlags;
        public ushort usButtonData;
        public uint ulRawButtons;
        public int lLastX;
        public int lLastY;
        public uint ulExtraInformation;
    }

    [StructLayout(LayoutKind.Explicit)]
    public struct RAWINPUTDATA
    {
        [FieldOffset(0)]
        public RAWMOUSE mouse;

        [FieldOffset(0)]
        public RAWKEYBOARD keyboard;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct RAWINPUT
    {
        public RAWINPUTHEADER header;
        public RAWINPUTDATA data;
    }

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool RegisterRawInputDevices(
        [In] RAWINPUTDEVICE[] pRawInputDevices,
        uint uiNumDevices,
        uint cbSize);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint GetRawInputData(
        IntPtr hRawInput,
        uint uiCommand,
        IntPtr pData,
        ref uint pcbSize,
        uint cbSizeHeader);
}
