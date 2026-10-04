using PhoneGamepad.Receiver.Security;
using Xunit;

namespace PhoneGamepad.Tests.Security;

public class SequenceWraparoundTests
{
    [Fact]
    public void SequenceValidator_StandardMonotonicIncrement_Accepts()
    {
        Assert.True(SequenceValidator.IsNewer(2, 1));
        Assert.True(SequenceValidator.IsNewer(100, 99));
        Assert.False(SequenceValidator.IsNewer(1, 2)); // Out of order
        Assert.False(SequenceValidator.IsNewer(10, 10)); // Duplicate
    }

    [Fact]
    public void SequenceValidator_WraparoundNearMaxUint_AcceptsNewer()
    {
        uint lastSeq = uint.MaxValue - 1; // 0xFFFFFFFE
        uint nextSeq = 1;

        Assert.True(SequenceValidator.IsNewer(nextSeq, lastSeq));
        Assert.False(SequenceValidator.IsNewer(lastSeq, nextSeq));
    }

    [Fact]
    public void SequenceTracker_RejectsDuplicatesAndStalePackets()
    {
        var tracker = new SequenceTracker();
        Assert.True(tracker.TryAccept(1));
        Assert.True(tracker.TryAccept(2));
        Assert.False(tracker.TryAccept(2)); // Duplicate
        Assert.False(tracker.TryAccept(1)); // Stale
        Assert.True(tracker.TryAccept(10));
        Assert.False(tracker.TryAccept(9)); // Stale
    }
}
