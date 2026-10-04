/**
 * @aegis-contract
 * @claim Input Sink Abstraction Interface
 * @true Declares KBM and Pad input methods and emergency neutralization contract
 * @false Contains no implementation shortcuts
 */

namespace PhoneGamepad.Receiver.Sinks;

public interface IInputSink : IDisposable
{
    void SendInputKbm(ReadOnlySpan<byte> keyBitmap128, byte mouseButtons, int deltaX, int deltaY);
    void SendKey(ushort scanCode, bool isExtended, bool isKeyUp);
    void SendMouseButtons(byte mouseButtons);
    void SendMouseMove(int deltaX, int deltaY);
    void SendInputPad(ushort buttons, byte lt, byte rt, short lx, short ly, short rx, short ry);
    void NeutralizeAll();

    bool IsKbmMode { get; }
    bool IsPadMode { get; }

    IReadOnlySet<(ushort ScanCode, bool IsExtended)> GetHeldKeys();
    byte GetHeldMouseButtons();
}
