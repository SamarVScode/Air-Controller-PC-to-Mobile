using PhoneGamepad.Receiver.Engine;
using Xunit;

namespace PhoneGamepad.Tests.Engine;

public class LookDeltaProcessorTests
{
    [Fact]
    public void ProcessLook_InitialPacket_EstablishesBaselineWithoutMotion()
    {
        var processor = new LookDeltaProcessor();
        var (dx, dy) = processor.ProcessLook(1000, -500, 0);

        Assert.Equal(0, dx);
        Assert.Equal(0, dy);
    }

    [Fact]
    public void ProcessLook_ConsecutiveMovement_CalculatesExactDelta()
    {
        var processor = new LookDeltaProcessor();
        processor.ProcessLook(100, 200, 0);

        var (dx, dy) = processor.ProcessLook(120, 195, 10);
        Assert.Equal(20, dx);
        Assert.Equal(-5, dy);
    }

    [Fact]
    public void ProcessLook_SilenceGapOver250ms_RebaselinesWithoutTeleport()
    {
        var processor = new LookDeltaProcessor();
        processor.ProcessLook(100, 100, 0);

        // Gap of 300ms (> 250ms threshold)
        var (dx, dy) = processor.ProcessLook(9000, 9000, 300);

        Assert.Equal(0, dx);
        Assert.Equal(0, dy);
    }

    [Fact]
    public void ProcessLook_ExceedingLimit_ClampsTo4000()
    {
        var processor = new LookDeltaProcessor();
        processor.ProcessLook(0, 0, 0);

        bool warned = false;
        processor.ClampingWarningLogged += (_, _) => warned = true;

        var (dx, dy) = processor.ProcessLook(5000, -7000, 10);
        Assert.Equal(4000, dx);
        Assert.Equal(-4000, dy);
        Assert.True(warned);
    }

    [Fact]
    public void ProcessLook_FractionalCarry_PreservesSubPixelMovement()
    {
        var processor = new LookDeltaProcessor { LookScale = 0.5f };
        processor.ProcessLook(0, 0, 0);

        // Delta 1 * 0.5 = 0.5 -> rounds to 1 (remainder -0.5) or 0
        var (dx1, _) = processor.ProcessLook(1, 0, 10);
        var (dx2, _) = processor.ProcessLook(2, 0, 20);

        Assert.Equal(1, dx1 + dx2);
    }
}
