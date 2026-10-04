/**
 * @aegis-contract
 * @claim Protocol constants definition for PhoneGamepad Wire Protocol v1
 * @true Defines port 47789, magic 0x4750 (0x5047 little-endian), version 1, and authoritative packet sizes
 * @false Contains no undefined packet types or incorrect byte sizes
 */

namespace PhoneGamepad.Receiver.Protocol;

public static class ProtocolConstants
{
    public const int DefaultPort = 47789;
    public const ushort Magic = 0x5047; // 0x47 ('G'), 0x50 ('P') in Little-Endian
    public const byte MagicByte0 = 0x47;
    public const byte MagicByte1 = 0x50;
    public const byte Version = 1;

    public const byte TypeInputKbm = 0;
    public const byte TypeRumble = 1;
    public const byte TypePing = 2;
    public const byte TypePong = 3;
    public const byte TypeHello = 4;
    public const byte TypeHelloAck = 5;
    public const byte TypeBye = 6;
    public const byte TypeInputPad = 7;
    public const byte TypeStatus = 8;

    public const int HeaderSize = 12;
    public const int MacSize = 8;

    public const int SizeInputKbm = 46;
    public const int SizeRumble = 22;
    public const int SizePing = 28;
    public const int SizePong = 28;
    public const int SizeHello = 36;
    public const int SizeHelloAck = 36;
    public const int SizeBye = 20;
    public const int SizeInputPad = 32;
    public const int SizeStatus = 21;

    public static int GetExpectedPacketSize(byte type) => type switch
    {
        TypeInputKbm => SizeInputKbm,
        TypeRumble => SizeRumble,
        TypePing => SizePing,
        TypePong => SizePong,
        TypeHello => SizeHello,
        TypeHelloAck => SizeHelloAck,
        TypeBye => SizeBye,
        TypeInputPad => SizeInputPad,
        TypeStatus => SizeStatus,
        _ => -1
    };
}
