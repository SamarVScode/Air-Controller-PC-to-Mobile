using System.Buffers.Binary;
using PhoneGamepad.Receiver.Protocol;
using Xunit;

namespace PhoneGamepad.Tests.Protocol;

public class PacketSerializationTests
{
    private readonly byte[] _testKey = Convert.FromHexString("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    [Fact]
    public void InputKbm_ExactSizeAndOffsetsMatchSpecification()
    {
        byte[] keyBitmap = new byte[16];
        keyBitmap[0] = 0x01; // Key 0 (A)
        byte mouseButtons = 0x03; // LMB + RMB
        int lookX = 1200;
        int lookY = -350;

        byte[] packet = PacketCodec.EncodeInputKbm(0x12345678, 42, keyBitmap, mouseButtons, lookX, lookY, _testKey);

        Assert.Equal(ProtocolConstants.SizeInputKbm, packet.Length);
        Assert.Equal(0x47, packet[0]);
        Assert.Equal(0x50, packet[1]);
        Assert.Equal(1, packet[2]);
        Assert.Equal(ProtocolConstants.TypeInputKbm, packet[3]);

        bool decoded = PacketCodec.TryDecodeInputKbm(packet, out var parsed);
        Assert.True(decoded);
        Assert.Equal(0x12345678u, parsed.Header.Session);
        Assert.Equal(42u, parsed.Header.Seq);
        Assert.Equal(0x01, parsed.KeysBitmap[0]);
        Assert.Equal(mouseButtons, parsed.MouseButtons);
        Assert.Equal(lookX, parsed.LookX);
        Assert.Equal(lookY, parsed.LookY);
    }

    [Fact]
    public void HelloAndHelloAck_CorrectPayloadAndNonceIntegrity()
    {
        byte[] clientNonce = new byte[16];
        clientNonce[0] = 0xAA;
        byte[] serverNonce = new byte[16];
        serverNonce[0] = 0xBB;

        byte[] helloPacket = PacketCodec.EncodeHello(clientNonce, _testKey);
        Assert.Equal(ProtocolConstants.SizeHello, helloPacket.Length);
        Assert.True(PacketCodec.TryDecodeHello(helloPacket, out var hello));
        Assert.Equal(0xAA, hello.ClientNonce[0]);

        byte[] helloAckPacket = PacketCodec.EncodeHelloAck(serverNonce, clientNonce, _testKey);
        Assert.Equal(ProtocolConstants.SizeHelloAck, helloAckPacket.Length);
        Assert.True(PacketCodec.TryDecodeHelloAck(helloAckPacket, out var helloAck));
        Assert.Equal(0xBB, helloAck.ServerNonce[0]);
    }

    [Fact]
    public void ByeAndPingPong_SerializationAccuracy()
    {
        byte[] bye = PacketCodec.EncodeBye(0x5555, 10, _testKey);
        Assert.Equal(ProtocolConstants.SizeBye, bye.Length);

        ulong timestamp = 1728000000000;
        byte[] ping = PacketCodec.EncodePing(0x5555, 11, timestamp, _testKey);
        Assert.Equal(ProtocolConstants.SizePing, ping.Length);

        ulong readTs = BinaryPrimitives.ReadUInt64LittleEndian(ping.AsSpan(12, 8));
        Assert.Equal(timestamp, readTs);
    }
}
