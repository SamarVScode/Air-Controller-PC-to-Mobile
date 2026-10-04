using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;

namespace InputTester;

public partial class MainWindow : Window
{
    private HwndSource? _hwndSource;
    private long _totalDx = 0;
    private long _totalDy = 0;
    private readonly HashSet<string> _heldKeys = new();
    private readonly HashSet<string> _heldMouseButtons = new();

    public MainWindow()
    {
        InitializeComponent();
    }

    private void Window_Loaded(object sender, RoutedEventArgs e)
    {
        var helper = new WindowInteropHelper(this);
        _hwndSource = HwndSource.FromHwnd(helper.Handle);
        _hwndSource?.AddHook(HwndHook);

        RegisterRawInput(helper.Handle);
        Log("Initialized Win32 Raw Input devices with RIDEV_INPUTSINK.");
    }

    private void RegisterRawInput(IntPtr hwnd)
    {
        var devices = new RawInput.RAWINPUTDEVICE[2];

        // Keyboard
        devices[0] = new RawInput.RAWINPUTDEVICE
        {
            usUsagePage = 0x01, // Generic Desktop
            usUsage = 0x06,     // Keyboard
            dwFlags = RawInput.RIDEV_INPUTSINK,
            hwndTarget = hwnd
        };

        // Mouse
        devices[1] = new RawInput.RAWINPUTDEVICE
        {
            usUsagePage = 0x01, // Generic Desktop
            usUsage = 0x02,     // Mouse
            dwFlags = RawInput.RIDEV_INPUTSINK,
            hwndTarget = hwnd
        };

        bool ok = RawInput.RegisterRawInputDevices(devices, (uint)devices.Length, (uint)Marshal.SizeOf<RawInput.RAWINPUTDEVICE>());
        if (!ok)
        {
            int err = Marshal.GetLastWin32Error();
            Log($"[ERROR] Failed to register Raw Input devices: Win32 error {err}");
        }
    }

    private IntPtr HwndHook(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == RawInput.WM_INPUT)
        {
            ProcessRawInput(lParam);
        }
        return IntPtr.Zero;
    }

    private void ProcessRawInput(IntPtr hRawInput)
    {
        uint size = 0;
        uint headerSize = (uint)Marshal.SizeOf<RawInput.RAWINPUTHEADER>();

        RawInput.GetRawInputData(hRawInput, RawInput.RID_INPUT, IntPtr.Zero, ref size, headerSize);
        if (size == 0) return;

        IntPtr buffer = Marshal.AllocHGlobal((int)size);
        try
        {
            if (RawInput.GetRawInputData(hRawInput, RawInput.RID_INPUT, buffer, ref size, headerSize) == size)
            {
                var raw = Marshal.PtrToStructure<RawInput.RAWINPUT>(buffer);
                if (raw.header.dwType == RawInput.RIM_TYPEKEYBOARD)
                {
                    HandleKeyboard(raw.data.keyboard);
                }
                else if (raw.header.dwType == RawInput.RIM_TYPEMOUSE)
                {
                    HandleMouse(raw.data.mouse);
                }
            }
        }
        finally
        {
            Marshal.FreeHGlobal(buffer);
        }
    }

    private void HandleKeyboard(RawInput.RAWKEYBOARD kb)
    {
        bool isDown = (kb.Flags & RawInput.RI_KEY_BREAK) == 0;
        bool isE0 = (kb.Flags & RawInput.RI_KEY_E0) != 0;
        bool isE1 = (kb.Flags & RawInput.RI_KEY_E1) != 0;
        string ext = isE0 ? " [E0]" : (isE1 ? " [E1]" : "");
        string state = isDown ? "DOWN" : "UP  ";

        string keyName = ((System.Windows.Input.Key)System.Windows.Input.KeyInterop.KeyFromVirtualKey(kb.VKey)).ToString();
        string entry = $"{DateTime.Now:HH:mm:ss.fff} | KB  | {state} | Scan: 0x{kb.MakeCode:X2} ({kb.MakeCode,3}) | VK: 0x{kb.VKey:X2} ({keyName,-12}){ext}";
        
        Log(entry);

        string trackerKey = $"{keyName}(0x{kb.MakeCode:X2})";
        if (isDown) _heldKeys.Add(trackerKey);
        else _heldKeys.Remove(trackerKey);

        TxtHeldKeys.Text = _heldKeys.Count > 0 ? string.Join(", ", _heldKeys) : "None";
    }

    private void HandleMouse(RawInput.RAWMOUSE m)
    {
        bool hasMove = m.lLastX != 0 || m.lLastY != 0;
        if (hasMove)
        {
            _totalDx += m.lLastX;
            _totalDy += m.lLastY;
            TxtMouseTotals.Text = $"{_totalDx}, {_totalDy}";

            if (ChkFilterMouseMove.IsChecked == true)
            {
                Log($"{DateTime.Now:HH:mm:ss.fff} | MOUSE_MOVE | dx: {m.lLastX,+5}, dy: {m.lLastY,+5} | Flags: 0x{m.usFlags:X4}");
            }
        }

        if (m.usButtonFlags != 0)
        {
            void CheckBtn(ushort downFlag, ushort upFlag, string name)
            {
                if ((m.usButtonFlags & downFlag) != 0)
                {
                    Log($"{DateTime.Now:HH:mm:ss.fff} | MOUSE_BTN  | {name} DOWN");
                    _heldMouseButtons.Add(name);
                }
                if ((m.usButtonFlags & upFlag) != 0)
                {
                    Log($"{DateTime.Now:HH:mm:ss.fff} | MOUSE_BTN  | {name} UP");
                    _heldMouseButtons.Remove(name);
                }
            }

            CheckBtn(RawInput.RI_MOUSE_LEFT_BUTTON_DOWN, RawInput.RI_MOUSE_LEFT_BUTTON_UP, "LMB");
            CheckBtn(RawInput.RI_MOUSE_RIGHT_BUTTON_DOWN, RawInput.RI_MOUSE_RIGHT_BUTTON_UP, "RMB");
            CheckBtn(RawInput.RI_MOUSE_MIDDLE_BUTTON_DOWN, RawInput.RI_MOUSE_MIDDLE_BUTTON_UP, "MMB");
            CheckBtn(RawInput.RI_MOUSE_BUTTON_4_DOWN, RawInput.RI_MOUSE_BUTTON_4_UP, "X1");
            CheckBtn(RawInput.RI_MOUSE_BUTTON_5_DOWN, RawInput.RI_MOUSE_BUTTON_5_UP, "X2");

            TxtMouseButtons.Text = _heldMouseButtons.Count > 0 ? string.Join(", ", _heldMouseButtons) : "None";
        }
    }

    private void Log(string message)
    {
        LstEvents.Items.Add(message);
        if (ChkAutoScroll.IsChecked == true)
        {
            LstEvents.ScrollIntoView(LstEvents.Items[^1]);
        }
    }

    private void BtnClear_Click(object sender, RoutedEventArgs e)
    {
        LstEvents.Items.Clear();
        _totalDx = 0;
        _totalDy = 0;
        TxtMouseTotals.Text = "0, 0";
    }

    private async void BtnSelfTest_Click(object sender, RoutedEventArgs e)
    {
        BtnSelfTest.IsEnabled = false;
        try
        {
            await SendInputTester.RunInjectionTestAsync(Log);
        }
        finally
        {
            BtnSelfTest.IsEnabled = true;
        }
    }
}
