/**
 * @aegis-contract
 * @claim Binary encoding and decoding of Phone-as-Gamepad v1 packets
 * @true Implements Little-Endian framing, byte layouts for types 0..8, and HMAC calculations
 * @false Contains no endianness ambiguity or truncation errors
 */

using System.Buffers.Binary;
using System.Security.Cryptography;

namespace PhoneGamepad.Receiver.Protocol;

public readonly record struct PacketHeader(ushort Magic, byte Version, byte Type, uint Session, uint Seq);

public readonly record struct InputKbmPacket(
    PacketHeader Header,
    byte[] KeysBitmap,
    byte MouseButtons,
    byte Flags,
    int LookX,
    int LookY,
    ulong Mac
);

public readonly record struct HelloPacket(PacketHeader Header, byte[] ClientNonce, ulong Mac);
public readonly record struct HelloAckPacket(PacketHeader Header, byte[] ServerNonce, ulong Mac);
public readonly record struct ByePacket(PacketHeader Header, ulong Mac);
public readonly record struct PingPacket(PacketHeader Header, ulong Timestamp, ulong Mac);
public readonly record struct PongPacket(PacketHeader Header, ulong Timestamp, ulong Mac);
public readonly record struct StatusPacket(PacketHeader Header, byte Flags, ulong Mac);

public static class PacketCodec
{
    public static void EncodeHeader(Span<byte> destination, PacketHeader header)
    {
        BinaryPrimitives.WriteUInt16LittleEndian(destination[..2], header.Magic);
        destination[2] = header.Version;
        destination[3] = header.Type;
        BinaryPrimitives.WriteUInt32LittleEndian(destination.Slice(4, 4), header.Session);
        BinaryPrimitives.WriteUInt32LittleEndian(destination.Slice(8, 4), header.Seq);
    }

    public static bool TryDecodeHeader(ReadOnlySpan<byte> source, out PacketHeader header)
    {
        if (source.Length < ProtocolConstants.HeaderSize)
        {
            header = default;
            return false;
        }

        ushort magic = BinaryPrimitives.ReadUInt16LittleEndian(source[..2]);
        byte version = source[2];
        if (magic != ProtocolConstants.Magic || version != ProtocolConstants.Version)
        {
            header = default;
            return false;
        }

        header = new PacketHeader(
            Magic: magic,
            Version: version,
            Type: source[3],
            Session: BinaryPrimitives.ReadUInt32LittleEndian(source.Slice(4, 4)),
            Seq: BinaryPrimitives.ReadUInt32LittleEndian(source.Slice(8, 4))
        );
        return true;
    }

    public static byte[] EncodeInputKbm(uint session, uint seq, ReadOnlySpan<byte> keysBitmap16, byte mouse, int lookX, int lookY, byte[] key)
    {
        byte[] buffer = new byte[ProtocolConstants.SizeInputKbm];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, ProtocolConstants.TypeInputKbm, session, seq);
        EncodeHeader(buffer.AsSpan(0, 12), header);

        keysBitmap16[..16].CopyTo(buffer.AsSpan(12, 16));
        buffer[28] = mouse;
        buffer[29] = 0; // reserved flags
        BinaryPrimitives.WriteInt32LittleEndian(buffer.AsSpan(30, 4), lookX);
        BinaryPrimitives.WriteInt32LittleEndian(buffer.AsSpan(34, 4), lookY);

        AppendMac(buffer, key);
        return buffer;
    }

    public static bool TryDecodeInputKbm(ReadOnlySpan<byte> source, out InputKbmPacket packet)
    {
        if (source.Length != ProtocolConstants.SizeInputKbm || !TryDecodeHeader(source, out var header) || header.Type != ProtocolConstants.TypeInputKbm)
        {
            packet = default;
            return false;
        }

        byte[] keys = source.Slice(12, 16).ToArray();
        byte mouse = source[28];
        byte flags = source[29];
        int lookX = BinaryPrimitives.ReadInt32LittleEndian(source.Slice(30, 4));
        int lookY = BinaryPrimitives.ReadInt32LittleEndian(source.Slice(34, 4));
        ulong mac = BinaryPrimitives.ReadUInt64LittleEndian(source.Slice(38, 8));

        packet = new InputKbmPacket(header, keys, mouse, flags, lookX, lookY, mac);
        return true;
    }

    public static byte[] EncodeHello(ReadOnlySpan<byte> clientNonce16, byte[] key)
    {
        byte[] buffer = new byte[ProtocolConstants.SizeHello];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, ProtocolConstants.TypeHello, 0, 0);
        EncodeHeader(buffer.AsSpan(0, 12), header);
        clientNonce16[..16].CopyTo(buffer.AsSpan(12, 16));
        AppendMac(buffer, key);
        return buffer;
    }

    public static bool TryDecodeHello(ReadOnlySpan<byte> source, out HelloPacket packet)
    {
        if (source.Length != ProtocolConstants.SizeHello || !TryDecodeHeader(source, out var header) || header.Type != ProtocolConstants.TypeHello)
        {
            packet = default;
            return false;
        }

        byte[] nonce = source.Slice(12, 16).ToArray();
        ulong mac = BinaryPrimitives.ReadUInt64LittleEndian(source.Slice(28, 8));
        packet = new HelloPacket(header, nonce, mac);
        return true;
    }

    public static byte[] EncodeHelloAck(ReadOnlySpan<byte> serverNonce16, ReadOnlySpan<byte> clientNonce16, byte[] key)
    {
        byte[] buffer = new byte[ProtocolConstants.SizeHelloAck];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, ProtocolConstants.TypeHelloAck, 0, 0);
        EncodeHeader(buffer.AsSpan(0, 12), header);
        serverNonce16[..16].CopyTo(buffer.AsSpan(12, 16));

        // MAC covers Header (12) + ServerNonce (16) + ClientNonce (16) = 44 bytes
        byte[] authPayload = new byte[44];
        buffer.AsSpan(0, 28).CopyTo(authPayload.AsSpan(0, 28));
        clientNonce16[..16].CopyTo(authPayload.AsSpan(28, 16));

        using var hmac = new HMACSHA256(key);
        byte[] hash = hmac.ComputeHash(authPayload);
        hash.AsSpan(0, 8).CopyTo(buffer.AsSpan(28, 8));

        return buffer;
    }

    public static bool TryDecodeHelloAck(ReadOnlySpan<byte> source, out HelloAckPacket packet)
    {
        if (source.Length != ProtocolConstants.SizeHelloAck || !TryDecodeHeader(source, out var header) || header.Type != ProtocolConstants.TypeHelloAck)
        {
            packet = default;
            return false;
        }

        byte[] nonce = source.Slice(12, 16).ToArray();
        ulong mac = BinaryPrimitives.ReadUInt64LittleEndian(source.Slice(28, 8));
        packet = new HelloAckPacket(header, nonce, mac);
        return true;
    }

    public static byte[] EncodeBye(uint session, uint seq, byte[] key)
    {
        byte[] buffer = new byte[ProtocolConstants.SizeBye];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, ProtocolConstants.TypeBye, session, seq);
        EncodeHeader(buffer.AsSpan(0, 12), header);
        AppendMac(buffer, key);
        return buffer;
    }

    public static byte[] EncodeStatus(uint session, uint seq, byte flags, byte[] key)
    {
        byte[] buffer = new byte[ProtocolConstants.SizeStatus];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, ProtocolConstants.TypeStatus, session, seq);
        EncodeHeader(buffer.AsSpan(0, 12), header);
        buffer[12] = flags;
        AppendMac(buffer, key);
        return buffer;
    }

    public static byte[] EncodePing(uint session, uint seq, ulong timestamp, byte[] key)
    {
        byte[] buffer = new byte[ProtocolConstants.SizePing];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, ProtocolConstants.TypePing, session, seq);
        EncodeHeader(buffer.AsSpan(0, 12), header);
        BinaryPrimitives.WriteUInt64LittleEndian(buffer.AsSpan(12, 8), timestamp);
        AppendMac(buffer, key);
        return buffer;
    }

    public static byte[] EncodePong(uint session, uint seq, ulong timestamp, byte[] key)
    {
        byte[] buffer = new byte[ProtocolConstants.SizePong];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, ProtocolConstants.TypePong, session, seq);
        EncodeHeader(buffer.AsSpan(0, 12), header);
        BinaryPrimitives.WriteUInt64LittleEndian(buffer.AsSpan(12, 8), timestamp);
        AppendMac(buffer, key);
        return buffer;
    }

    private static void AppendMac(byte[] packet, byte[] key)
    {
        int bodyLength = packet.Length - ProtocolConstants.MacSize;
        using var hmac = new HMACSHA256(key);
        byte[] hash = hmac.ComputeHash(packet, 0, bodyLength);
        Buffer.BlockCopy(hash, 0, packet, bodyLength, ProtocolConstants.MacSize);
    }
}
