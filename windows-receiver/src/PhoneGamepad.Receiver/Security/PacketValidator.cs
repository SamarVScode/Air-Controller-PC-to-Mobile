/**
 * @aegis-contract
 * @claim Strict Header, Session, and Constant-Time HMAC-SHA256 Packet Validation
 * @true Uses CryptographicOperations.FixedTimeEquals and strictly verifies length and magic
 * @false Permits no timing-attack vulnerabilities or malformed datagrams
 */

using System.Security.Cryptography;
using PhoneGamepad.Receiver.Protocol;

namespace PhoneGamepad.Receiver.Security;

public static class PacketValidator
{
    public static bool ValidateDatagram(
        ReadOnlySpan<byte> datagram,
        byte[] presharedKey,
        uint? activeSessionId,
        out PacketHeader header,
        ReadOnlySpan<byte> expectedClientNonceForHelloAck = default
    )
    {
        header = default;

        if (datagram.Length < ProtocolConstants.HeaderSize + ProtocolConstants.MacSize)
            return false;

        if (!PacketCodec.TryDecodeHeader(datagram, out header))
            return false;

        // Magic and version checks
        if (header.Magic != ProtocolConstants.Magic || header.Version != ProtocolConstants.Version)
            return false;

        // Size check
        int expectedSize = ProtocolConstants.GetExpectedPacketSize(header.Type);
        if (expectedSize != datagram.Length)
            return false;

        // Session check for normal stream packets
        if (header.Type != ProtocolConstants.TypeHello && header.Type != ProtocolConstants.TypeHelloAck)
        {
            if (!activeSessionId.HasValue || header.Session != activeSessionId.Value)
                return false;
        }

        // HMAC verification
        return VerifyMac(datagram, presharedKey, expectedClientNonceForHelloAck);
    }

    public static bool VerifyMac(
        ReadOnlySpan<byte> datagram,
        byte[] presharedKey,
        ReadOnlySpan<byte> expectedClientNonceForHelloAck = default
    )
    {
        if (datagram.Length < ProtocolConstants.MacSize) return false;

        int macOffset = datagram.Length - ProtocolConstants.MacSize;
        ReadOnlySpan<byte> receivedMac = datagram.Slice(macOffset, ProtocolConstants.MacSize);

        byte[] computedHash;
        using var hmac = new HMACSHA256(presharedKey);

        byte packetType = datagram[3];
        if (packetType == ProtocolConstants.TypeHelloAck)
        {
            if (expectedClientNonceForHelloAck.Length != 16) return false;

            byte[] authData = new byte[44];
            datagram.Slice(0, 28).CopyTo(authData.AsSpan(0, 28));
            expectedClientNonceForHelloAck.CopyTo(authData.AsSpan(28, 16));
            computedHash = hmac.ComputeHash(authData);
        }
        else
        {
            computedHash = hmac.ComputeHash(datagram[..macOffset].ToArray());
        }

        return CryptographicOperations.FixedTimeEquals(receivedMac, computedHash.AsSpan(0, 8));
    }
}
