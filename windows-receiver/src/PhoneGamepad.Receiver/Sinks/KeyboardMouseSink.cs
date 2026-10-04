/**
 * @aegis-contract
 * @claim Production Win32 SendInput Keyboard and Mouse Sink
 * @true Injects hardware scan codes via KEYEVENTF_SCANCODE and relative motion via MOUSEEVENTF_MOVE
 * @true Implements instant NeutralizeAll to release all held keys and mouse buttons
 * @false Does not use VK-only injection or absolute cursor placement
 */

using System.Runtime.InteropServices;
using PhoneGamepad.Receiver.Protocol;

namespace PhoneGamepad.Receiver.Sinks;

public class KeyboardMouseSink : IInputSink
{
    private readonly object _lock = new();
    private readonly HashSet<(ushort ScanCode, bool IsExtended)> _heldKeys = new();
    private byte _heldMouseButtons = 0;

    public Action<Win32Native.INPUT[]>? RawInputHook { get; set; }

    public bool IsKbmMode => true;
    public bool IsPadMode => false;

    public void SendInputKbm(ReadOnlySpan<byte> keyBitmap128, byte mouseButtons, int deltaX, int deltaY)
    {
        lock (_lock)
        {
            if (deltaX != 0 || deltaY != 0)
            {
                SendMouseMove(deltaX, deltaY);
            }

            SendMouseButtons(mouseButtons);

            // Diff keys
            for (byte id = 0; id < 62; id++)
            {
                bool isSet = (keyBitmap128[id / 8] & (1 << (id % 8))) != 0;
                if (!KeyMap.TryGetMapping(id, out var mapping)) continue;

                var keyTuple = (mapping.MakeCode, mapping.IsExtended);
                bool wasDown = _heldKeys.Contains(keyTuple);

                if (isSet && !wasDown)
                {
                    SendKey(mapping.MakeCode, mapping.IsExtended, isKeyUp: false);
                    _heldKeys.Add(keyTuple);
                }
                else if (!isSet && wasDown)
                {
                    SendKey(mapping.MakeCode, mapping.IsExtended, isKeyUp: true);
                    _heldKeys.Remove(keyTuple);
                }
            }
        }
    }

    public void SendKey(ushort scanCode, bool isExtended, bool isKeyUp)
    {
        lock (_lock)
        {
            var input = new Win32Native.INPUT
            {
                type = Win32Native.INPUT_KEYBOARD,
                u = new Win32Native.InputUnion
                {
                    ki = new Win32Native.KEYBDINPUT
                    {
                        wVk = 0,
                        wScan = scanCode,
                        dwFlags = Win32Native.KEYEVENTF_SCANCODE
                            | (isExtended ? Win32Native.KEYEVENTF_EXTENDEDKEY : 0)
                            | (isKeyUp ? Win32Native.KEYEVENTF_KEYUP : 0),
                        time = 0,
                        dwExtraInfo = IntPtr.Zero
                    }
                }
            };

            ExecuteInputs([input]);

            if (isKeyUp)
                _heldKeys.Remove((scanCode, isExtended));
            else
                _heldKeys.Add((scanCode, isExtended));
        }
    }

    public void SendMouseButtons(byte newButtons)
    {
        lock (_lock)
        {
            byte diff = (byte)(_heldMouseButtons ^ newButtons);
            if (diff == 0) return;

            var inputs = new List<Win32Native.INPUT>();

            // Bit 0: Left
            if ((diff & 0x01) != 0)
            {
                bool down = (newButtons & 0x01) != 0;
                inputs.Add(CreateMouseInput(down ? Win32Native.MOUSEEVENTF_LEFTDOWN : Win32Native.MOUSEEVENTF_LEFTUP));
            }
            // Bit 1: Right
            if ((diff & 0x02) != 0)
            {
                bool down = (newButtons & 0x02) != 0;
                inputs.Add(CreateMouseInput(down ? Win32Native.MOUSEEVENTF_RIGHTDOWN : Win32Native.MOUSEEVENTF_RIGHTUP));
            }
            // Bit 2: Middle
            if ((diff & 0x04) != 0)
            {
                bool down = (newButtons & 0x04) != 0;
                inputs.Add(CreateMouseInput(down ? Win32Native.MOUSEEVENTF_MIDDLEDOWN : Win32Native.MOUSEEVENTF_MIDDLEUP));
            }
            // Bit 3: X1
            if ((diff & 0x08) != 0)
            {
                bool down = (newButtons & 0x08) != 0;
                inputs.Add(CreateMouseInput(
                    down ? Win32Native.MOUSEEVENTF_XDOWN : Win32Native.MOUSEEVENTF_XUP,
                    Win32Native.XBUTTON1
                ));
            }
            // Bit 4: X2
            if ((diff & 0x10) != 0)
            {
                bool down = (newButtons & 0x10) != 0;
                inputs.Add(CreateMouseInput(
                    down ? Win32Native.MOUSEEVENTF_XDOWN : Win32Native.MOUSEEVENTF_XUP,
                    Win32Native.XBUTTON2
                ));
            }

            if (inputs.Count > 0)
            {
                ExecuteInputs(inputs.ToArray());
            }

            _heldMouseButtons = newButtons;
        }
    }

    public void SendMouseMove(int deltaX, int deltaY)
    {
        if (deltaX == 0 && deltaY == 0) return;

        var input = new Win32Native.INPUT
        {
            type = Win32Native.INPUT_MOUSE,
            u = new Win32Native.InputUnion
            {
                mi = new Win32Native.MOUSEINPUT
                {
                    dx = deltaX,
                    dy = deltaY,
                    mouseData = 0,
                    dwFlags = Win32Native.MOUSEEVENTF_MOVE,
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            }
        };

        ExecuteInputs([input]);
    }

    public void SendInputPad(ushort buttons, byte lt, byte rt, short lx, short ly, short rx, short ry)
    {
        // Reserved for Phase 8 Gamepad Sink fallback
    }

    public void NeutralizeAll()
    {
        lock (_lock)
        {
            var inputs = new List<Win32Native.INPUT>();

            // Release all held keys
            foreach (var (scanCode, isExtended) in _heldKeys)
            {
                inputs.Add(new Win32Native.INPUT
                {
                    type = Win32Native.INPUT_KEYBOARD,
                    u = new Win32Native.InputUnion
                    {
                        ki = new Win32Native.KEYBDINPUT
                        {
                            wVk = 0,
                            wScan = scanCode,
                            dwFlags = Win32Native.KEYEVENTF_SCANCODE
                                | (isExtended ? Win32Native.KEYEVENTF_EXTENDEDKEY : 0)
                                | Win32Native.KEYEVENTF_KEYUP,
                            time = 0,
                            dwExtraInfo = IntPtr.Zero
                        }
                    }
                });
            }
            _heldKeys.Clear();

            // Release all mouse buttons
            if ((_heldMouseButtons & 0x01) != 0) inputs.Add(CreateMouseInput(Win32Native.MOUSEEVENTF_LEFTUP));
            if ((_heldMouseButtons & 0x02) != 0) inputs.Add(CreateMouseInput(Win32Native.MOUSEEVENTF_RIGHTUP));
            if ((_heldMouseButtons & 0x04) != 0) inputs.Add(CreateMouseInput(Win32Native.MOUSEEVENTF_MIDDLEUP));
            if ((_heldMouseButtons & 0x08) != 0) inputs.Add(CreateMouseInput(Win32Native.MOUSEEVENTF_XUP, Win32Native.XBUTTON1));
            if ((_heldMouseButtons & 0x10) != 0) inputs.Add(CreateMouseInput(Win32Native.MOUSEEVENTF_XUP, Win32Native.XBUTTON2));
            _heldMouseButtons = 0;

            if (inputs.Count > 0)
            {
                ExecuteInputs(inputs.ToArray());
            }
        }
    }

    public IReadOnlySet<(ushort ScanCode, bool IsExtended)> GetHeldKeys()
    {
        lock (_lock) return new HashSet<(ushort, bool)>(_heldKeys);
    }

    public byte GetHeldMouseButtons()
    {
        lock (_lock) return _heldMouseButtons;
    }

    private void ExecuteInputs(Win32Native.INPUT[] inputs)
    {
        if (RawInputHook != null)
        {
            RawInputHook(inputs);
            return;
        }

        Win32Native.SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Win32Native.INPUT>());
    }

    private static Win32Native.INPUT CreateMouseInput(uint flags, uint mouseData = 0) => new()
    {
        type = Win32Native.INPUT_MOUSE,
        u = new Win32Native.InputUnion
        {
            mi = new Win32Native.MOUSEINPUT
            {
                dx = 0,
                dy = 0,
                mouseData = mouseData,
                dwFlags = flags,
                time = 0,
                dwExtraInfo = IntPtr.Zero
            }
        }
    };

    public void Dispose()
    {
        NeutralizeAll();
        GC.SuppressFinalize(this);
    }
}

public static class Win32Native
{
    public const uint INPUT_MOUSE = 0;
    public const uint INPUT_KEYBOARD = 1;

    public const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
    public const uint KEYEVENTF_KEYUP = 0x0002;
    public const uint KEYEVENTF_SCANCODE = 0x0008;

    public const uint MOUSEEVENTF_MOVE = 0x0001;
    public const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    public const uint MOUSEEVENTF_LEFTUP = 0x0004;
    public const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    public const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    public const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    public const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    public const uint MOUSEEVENTF_XDOWN = 0x0080;
    public const uint MOUSEEVENTF_XUP = 0x0100;

    public const uint XBUTTON1 = 0x0001;
    public const uint XBUTTON2 = 0x0002;

    [StructLayout(LayoutKind.Sequential)]
    public struct INPUT
    {
        public uint type;
        public InputUnion u;
    }

    [StructLayout(LayoutKind.Explicit)]
    public struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint SendInput(uint nInputs, [In] INPUT[] pInputs, int cbSize);
}
