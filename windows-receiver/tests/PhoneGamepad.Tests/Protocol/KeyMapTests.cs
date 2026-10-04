using PhoneGamepad.Receiver.Protocol;
using Xunit;

namespace PhoneGamepad.Tests.Protocol;

public class KeyMapTests
{
    [Theory]
    [InlineData(0, "A", 0x1E, false)]
    [InlineData(22, "W", 0x11, false)]
    [InlineData(36, "Space", 0x39, false)]
    [InlineData(56, "UpArrow", 0x48, true)]
    [InlineData(57, "DownArrow", 0x50, true)]
    [InlineData(58, "LeftArrow", 0x4B, true)]
    [InlineData(59, "RightArrow", 0x4D, true)]
    [InlineData(60, "Backspace", 0x0E, false)]
    public void KeyMap_AuthoritativeMappingsMatchDocs(byte id, string expectedName, ushort expectedMakeCode, bool expectedExtended)
    {
        Assert.True(KeyMap.TryGetMapping(id, out var mapping));
        Assert.Equal(expectedName, mapping.Name);
        Assert.Equal(expectedMakeCode, mapping.MakeCode);
        Assert.Equal(expectedExtended, mapping.IsExtended);
    }

    [Fact]
    public void KeyMap_ReservedIdsReturnFalse()
    {
        for (byte id = 62; id < 128; id++)
        {
            Assert.False(KeyMap.TryGetMapping(id, out _));
        }
    }
}
