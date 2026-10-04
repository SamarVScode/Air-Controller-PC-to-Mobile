using PhoneGamepad.Receiver.Protocol;
using PhoneGamepad.Receiver.Security;
using Xunit;

namespace PhoneGamepad.Tests.Security;

public class PacketValidatorTests
{
    private readonly byte[] _key = Convert.FromHexString("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    [Fact]
    public void ValidateDatagram_ValidInputKbm_Passes()
    {
        byte[] packet = PacketCodec.EncodeInputKbm(0x1000, 1, new byte[16], 0, 0, 0, _key);

        bool valid = PacketValidator.ValidateDatagram(packet, _key, 0x1000, out var header);
        Assert.True(valid);
        Assert.Equal(0x1000u, header.Session);
    }

    [Fact]
    public void ValidateDatagram_CorruptedMac_FailsConstantTimeCheck()
    {
        byte[] packet = PacketCodec.EncodeInputKbm(0x1000, 1, new byte[16], 0, 0, 0, _key);
        packet[^1] ^= 0xFF; // Corrupt last MAC byte

        bool valid = PacketValidator.ValidateDatagram(packet, _key, 0x1000, out _);
        Assert.False(valid);
    }

    [Fact]
    public void ValidateDatagram_SessionMismatch_Fails()
    {
        byte[] packet = PacketCodec.EncodeInputKbm(0x1000, 1, new byte[16], 0, 0, 0, _key);

        bool valid = PacketValidator.ValidateDatagram(packet, _key, 0x9999, out _);
        Assert.False(valid);
    }

    [Fact]
    public void HelloAck_VerifiesWithMatchingClientNonce()
    {
        byte[] clientNonce = new byte[16];
        clientNonce[0] = 0x55;
        byte[] serverNonce = new byte[16];

        byte[] ackPacket = PacketCodec.EncodeHelloAck(serverNonce, clientNonce, _key);

        Assert.True(PacketValidator.VerifyMac(ackPacket, _key, clientNonce));

        byte[] wrongClientNonce = new byte[16];
        wrongClientNonce[0] = 0xAA;
        Assert.False(PacketValidator.VerifyMac(ackPacket, _key, wrongClientNonce));
    }
}
