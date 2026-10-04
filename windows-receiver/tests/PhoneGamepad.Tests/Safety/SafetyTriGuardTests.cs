using PhoneGamepad.Receiver.Safety;
using PhoneGamepad.Receiver.Sinks;
using Xunit;

namespace PhoneGamepad.Tests.Safety;

public class SafetyTriGuardTests
{
    private class MockSink : IInputSink
    {
        public bool Neutralized { get; private set; }
        public void NeutralizeAll() => Neutralized = true;
        public void Reset() => Neutralized = false;
        public void SendInputKbm(ReadOnlySpan<byte> k, byte m, int dx, int dy) { }
        public void SendKey(ushort s, bool e, bool u) { }
        public void SendMouseButtons(byte m) { }
        public void SendMouseMove(int dx, int dy) { }
        public void SendInputPad(ushort b, byte lt, byte rt, short lx, short ly, short rx, short ry) { }
        public bool IsKbmMode => true;
        public bool IsPadMode => false;
        public IReadOnlySet<(ushort ScanCode, bool IsExtended)> GetHeldKeys() => new HashSet<(ushort, bool)>();
        public byte GetHeldMouseButtons() => 0;
        public void Dispose() { }
    }

    [Fact]
    public void Watchdog_500msInactivity_TriggersNeutralize()
    {
        var sink = new MockSink();
        var guard = new SafetyTriGuard(sink, new FocusGuard { AnyWindowMode = true });

        guard.Arm();
        Assert.False(sink.Neutralized);

        // Simulate 600ms inactivity
        Thread.Sleep(550);
        guard.PollTick();

        Assert.True(sink.Neutralized);
    }

    [Fact]
    public void Disarm_ImmediatelyNeutralizes()
    {
        var sink = new MockSink();
        var guard = new SafetyTriGuard(sink, new FocusGuard { AnyWindowMode = true });

        guard.Arm();
        sink.Reset();

        guard.Disarm();
        Assert.True(sink.Neutralized);
        Assert.False(guard.IsArmed);
    }
}
