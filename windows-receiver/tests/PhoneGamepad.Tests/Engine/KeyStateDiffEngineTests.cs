using PhoneGamepad.Receiver.Engine;
using Xunit;

namespace PhoneGamepad.Tests.Engine;

public class KeyStateDiffEngineTests
{
    [Fact]
    public void ProcessInput_KeyDownImmediate_EmitsDownEvent()
    {
        var engine = new KeyStateDiffEngine(30);
        var events = new List<(ushort ScanCode, bool IsUp)>();

        byte[] bitmap = new byte[16];
        bitmap[0] = 0x01; // Key 0 (A) -> scan code 0x1E

        engine.ProcessInput(bitmap, 0, (code, _, isUp) => events.Add((code, isUp)));

        Assert.Single(events);
        Assert.Equal(0x1E, events[0].ScanCode);
        Assert.False(events[0].IsUp);
    }

    [Fact]
    public void ProcessInput_KeyUpBefore30ms_DelaysUntilMinimumHoldTime()
    {
        var engine = new KeyStateDiffEngine(30);
        var events = new List<(ushort ScanCode, bool IsUp)>();

        byte[] downBitmap = new byte[16];
        downBitmap[0] = 0x01; // Key 0 (A)
        engine.ProcessInput(downBitmap, 0, (code, _, isUp) => events.Add((code, isUp)));

        byte[] upBitmap = new byte[16]; // Key released after 10ms
        engine.ProcessInput(upBitmap, 10, (code, _, isUp) => events.Add((code, isUp)));

        // Key-up must NOT have been emitted yet (held for only 10ms < 30ms)
        Assert.Single(events);

        // Tick at 25ms (< 30ms)
        engine.Tick(25, (code, _, isUp) => events.Add((code, isUp)));
        Assert.Single(events);

        // Tick at 35ms (>= 30ms)
        engine.Tick(35, (code, _, isUp) => events.Add((code, isUp)));
        Assert.Equal(2, events.Count);
        Assert.True(events[1].IsUp);
    }

    [Fact]
    public void Neutralize_ImmediatelyReleasesKeysIgnoringHoldTime()
    {
        var engine = new KeyStateDiffEngine(30);
        var events = new List<(ushort ScanCode, bool IsUp)>();

        byte[] bitmap = new byte[16];
        bitmap[0] = 0x01; // Key down at t=0
        engine.ProcessInput(bitmap, 0, (code, _, isUp) => events.Add((code, isUp)));

        // Emergency Neutralize at t=5ms
        engine.Neutralize((code, _, isUp) => events.Add((code, isUp)));

        Assert.Equal(2, events.Count);
        Assert.True(events[1].IsUp);
    }
}
