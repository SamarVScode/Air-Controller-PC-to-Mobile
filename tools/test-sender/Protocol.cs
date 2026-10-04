using System.Security.Cryptography;

namespace TestSender;

public static class Protocol
{
    public const ushort Magic = 0x5047; // 0x47, 0x50 ('G', 'P' little-endian)
    public const byte Version = 1;

    public const byte TypeInputKbm  = 0;
    public const byte TypeRumble    = 1;
    public const byte TypePing      = 2;
    public const byte TypePong      = 3;
    public const byte TypeHello     = 4;
    public const byte TypeHelloAck  = 5;
    public const byte TypeBye       = 6;
    public const byte TypeInputPad  = 7;
    public const byte TypeStatus    = 8;

    public static byte[] BuildPacket(byte type, uint session, uint seq, byte[] payload, byte[] key)
    {
        int headerSize = 12;
        int payloadSize = payload.Length;
        int macSize = 8;
        byte[] packet = new byte[headerSize + payloadSize + macSize];

        // 1. Header (12 bytes)
        BitConverter.TryWriteBytes(packet.AsSpan(0, 2), Magic);
        packet[2] = Version;
        packet[3] = type;
        BitConverter.TryWriteBytes(packet.AsSpan(4, 4), session);
        BitConverter.TryWriteBytes(packet.AsSpan(8, 4), seq);

        // 2. Payload
        Buffer.BlockCopy(payload, 0, packet, headerSize, payloadSize);

        // 3. HMAC-SHA256 over header + payload
        using var hmac = new HMACSHA256(key);
        byte[] hash = hmac.ComputeHash(packet, 0, headerSize + payloadSize);
        Buffer.BlockCopy(hash, 0, packet, headerSize + payloadSize, macSize);

        return packet;
    }

    public static byte[] CreateInputKbmPayload(byte[] keyBitmap128, byte mouseButtons, int lookX, int lookY)
    {
        byte[] payload = new byte[26];
        Buffer.BlockCopy(keyBitmap128, 0, payload, 0, 16);
        payload[16] = mouseButtons;
        payload[17] = 0; // reserved flags
        BitConverter.TryWriteBytes(payload.AsSpan(18, 4), lookX);
        BitConverter.TryWriteBytes(payload.AsSpan(22, 4), lookY);
        return payload;
    }
}
