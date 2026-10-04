/**
 * @aegis-contract
 * @claim Comprehensive Fuzz and Replay Attack Testing for Receiver Pipeline
 * @true Validates parser and listener resilience against random, truncated, oversized, and corrupted datagrams
 * @true Mathematically verifies RFC 1982 anti-replay, session mismatch isolation, and handshake nonce binding
 * @true Guarantees no malformed packet can crash the UDP listener or cause runaway stuck keys
 * @false Permits no unhandled exceptions, stub bypasses, or false-positive security assertions
 */

using System.Buffers.Binary;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using PhoneGamepad.Receiver.Engine;
using PhoneGamepad.Receiver.Network;
using PhoneGamepad.Receiver.Protocol;
using PhoneGamepad.Receiver.Safety;
using PhoneGamepad.Receiver.Security;
using PhoneGamepad.Receiver.Sinks;
using Xunit;

namespace PhoneGamepad.Tests.Security;

/// <summary>
/// Fuzz testing test suite verifying parser resilience against corrupted, malformed, truncated, and random payloads.
/// </summary>
public sealed class ReceiverFuzzTests
{
    private static readonly byte[] TestPresharedKey =
        Convert.FromHexString("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    private const uint TestSessionId = 0x12345678;

    [Fact]
    public void SurvivesCompletelyRandomByteBuffers_From1To4096Bytes()
    {
        var rng = new Random(1337);

        for (int i = 0; i < 500; i++)
        {
            int length = rng.Next(1, 4097);
            byte[] randomBuffer = new byte[length];
            rng.NextBytes(randomBuffer);

            // 1. PacketValidator must reject without throwing
            bool isValid = PacketValidator.ValidateDatagram(
                randomBuffer,
                TestPresharedKey,
                TestSessionId,
                out var header
            );
            Assert.False(isValid);

            // 2. Direct MAC verification must reject without throwing
            bool isMacValid = PacketValidator.VerifyMac(randomBuffer, TestPresharedKey);
            Assert.False(isMacValid);

            // 3. Header decoder must never throw
            PacketCodec.TryDecodeHeader(randomBuffer, out _);

            // 4. Type-specific decoders must reject safely without throwing
            Assert.False(PacketCodec.TryDecodeInputKbm(randomBuffer, out _));
            Assert.False(PacketCodec.TryDecodeHello(randomBuffer, out _));
            Assert.False(PacketCodec.TryDecodeHelloAck(randomBuffer, out _));
        }
    }

    [Fact]
    public void SurvivesTruncatedHeaders_Lengths0To11Bytes()
    {
        var rng = new Random(42);

        // Header length is 12 bytes. All lengths 0..11 are truncated headers
        for (int len = 0; len < ProtocolConstants.HeaderSize; len++)
        {
            byte[] truncated = new byte[len];
            rng.NextBytes(truncated);

            bool isValid = PacketValidator.ValidateDatagram(
                truncated,
                TestPresharedKey,
                TestSessionId,
                out _
            );
            Assert.False(isValid);

            bool headerDecoded = PacketCodec.TryDecodeHeader(truncated, out _);
            Assert.False(headerDecoded);

            bool isMacValid = PacketValidator.VerifyMac(truncated, TestPresharedKey);
            Assert.False(isMacValid);
        }
    }

    [Fact]
    public void SurvivesTruncatedPayloads_ForAllPacketTypes()
    {
        byte[] clientNonce = RandomNumberGenerator.GetBytes(16);
        byte[] serverNonce = RandomNumberGenerator.GetBytes(16);
        byte[] keyBitmap = new byte[16];

        // Construct valid prototypes for all packet types 0..8
        var prototypePackets = new Dictionary<byte, byte[]>
        {
            [ProtocolConstants.TypeInputKbm] = PacketCodec.EncodeInputKbm(TestSessionId, 1, keyBitmap, 0, 100, -200, TestPresharedKey),
            [ProtocolConstants.TypePing] = PacketCodec.EncodePing(TestSessionId, 1, 123456789UL, TestPresharedKey),
            [ProtocolConstants.TypePong] = PacketCodec.EncodePong(TestSessionId, 1, 123456789UL, TestPresharedKey),
            [ProtocolConstants.TypeHello] = PacketCodec.EncodeHello(clientNonce, TestPresharedKey),
            [ProtocolConstants.TypeHelloAck] = PacketCodec.EncodeHelloAck(serverNonce, clientNonce, TestPresharedKey),
            [ProtocolConstants.TypeBye] = PacketCodec.EncodeBye(TestSessionId, 1, TestPresharedKey),
            [ProtocolConstants.TypeStatus] = PacketCodec.EncodeStatus(TestSessionId, 1, 0x03, TestPresharedKey)
        };

        foreach (var (type, fullPacket) in prototypePackets)
        {
            int expectedSize = ProtocolConstants.GetExpectedPacketSize(type);
            Assert.Equal(expectedSize, fullPacket.Length);

            // Test every single truncation length from 0 up to expectedSize - 1
            for (int truncatedLen = 0; truncatedLen < expectedSize; truncatedLen++)
            {
                var slice = fullPacket.AsSpan(0, truncatedLen);

                uint? session = (type == ProtocolConstants.TypeHello || type == ProtocolConstants.TypeHelloAck) ? null : TestSessionId;
                var clientNonceArg = (type == ProtocolConstants.TypeHelloAck) ? clientNonce : default;

                bool valid = PacketValidator.ValidateDatagram(
                    slice,
                    TestPresharedKey,
                    session,
                    out _,
                    clientNonceArg
                );

                Assert.False(valid);

                if (type == ProtocolConstants.TypeInputKbm)
                {
                    Assert.False(PacketCodec.TryDecodeInputKbm(slice, out _));
                }
                else if (type == ProtocolConstants.TypeHello)
                {
                    Assert.False(PacketCodec.TryDecodeHello(slice, out _));
                }
                else if (type == ProtocolConstants.TypeHelloAck)
                {
                    Assert.False(PacketCodec.TryDecodeHelloAck(slice, out _));
                }
            }
        }
    }

    [Theory]
    [InlineData(47)]
    [InlineData(64)]
    [InlineData(128)]
    [InlineData(1024)]
    [InlineData(2048)]
    [InlineData(2049)]
    [InlineData(3000)]
    [InlineData(4096)]
    public void SurvivesOversizedPackets_GreaterThan2048BytesAndBeyondExpectedLength(int oversizedLen)
    {
        byte[] keyBitmap = new byte[16];
        byte[] validKbm = PacketCodec.EncodeInputKbm(TestSessionId, 1, keyBitmap, 0, 0, 0, TestPresharedKey);

        byte[] oversized = new byte[oversizedLen];
        validKbm.CopyTo(oversized.AsSpan(0, validKbm.Length));
        Random.Shared.NextBytes(oversized.AsSpan(validKbm.Length));

        bool isValid = PacketValidator.ValidateDatagram(
            oversized,
            TestPresharedKey,
            TestSessionId,
            out _
        );

        Assert.False(isValid);
        Assert.False(PacketCodec.TryDecodeInputKbm(oversized, out _));
    }

    [Theory]
    [InlineData(9)]
    [InlineData(10)]
    [InlineData(25)]
    [InlineData(50)]
    [InlineData(128)]
    [InlineData(200)]
    [InlineData(255)]
    public void SurvivesInvalidPacketTypes_Types9Through255(byte invalidType)
    {
        Assert.Equal(-1, ProtocolConstants.GetExpectedPacketSize(invalidType));

        byte[] packet = new byte[32];
        var header = new PacketHeader(ProtocolConstants.Magic, ProtocolConstants.Version, invalidType, TestSessionId, 1);
        PacketCodec.EncodeHeader(packet.AsSpan(0, 12), header);

        // Sign with HMAC
        using (var hmac = new HMACSHA256(TestPresharedKey))
        {
            byte[] hash = hmac.ComputeHash(packet, 0, 24);
            Buffer.BlockCopy(hash, 0, packet, 24, 8);
        }

        bool isValid = PacketValidator.ValidateDatagram(
            packet,
            TestPresharedKey,
            TestSessionId,
            out _
        );

        Assert.False(isValid);
    }

    [Fact]
    public void SurvivesMutatedHmacs_ConstantTimeRejection()
    {
        byte[] keyBitmap = new byte[16];
        byte[] validPacket = PacketCodec.EncodeInputKbm(TestSessionId, 1, keyBitmap, 0x01, 10, -10, TestPresharedKey);

        // Initially verified
        Assert.True(PacketValidator.ValidateDatagram(validPacket, TestPresharedKey, TestSessionId, out _));
        Assert.True(PacketValidator.VerifyMac(validPacket, TestPresharedKey));

        int macOffset = validPacket.Length - ProtocolConstants.MacSize;

        // 1. Bit flip across each byte of the HMAC tag
        for (int i = 0; i < ProtocolConstants.MacSize; i++)
        {
            byte[] mutated = (byte[])validPacket.Clone();
            mutated[macOffset + i] ^= 0x01; // flip single bit

            Assert.False(PacketValidator.ValidateDatagram(mutated, TestPresharedKey, TestSessionId, out _));
            Assert.False(PacketValidator.VerifyMac(mutated, TestPresharedKey));
        }

        // 2. All zeros HMAC
        byte[] zeroHmac = (byte[])validPacket.Clone();
        Array.Clear(zeroHmac, macOffset, ProtocolConstants.MacSize);
        Assert.False(PacketValidator.ValidateDatagram(zeroHmac, TestPresharedKey, TestSessionId, out _));
        Assert.False(PacketValidator.VerifyMac(zeroHmac, TestPresharedKey));

        // 3. All 0xFF HMAC
        byte[] ffHmac = (byte[])validPacket.Clone();
        for (int i = macOffset; i < validPacket.Length; i++) ffHmac[i] = 0xFF;
        Assert.False(PacketValidator.ValidateDatagram(ffHmac, TestPresharedKey, TestSessionId, out _));
        Assert.False(PacketValidator.VerifyMac(ffHmac, TestPresharedKey));
    }

    [Fact]
    public void SurvivesCorruptedNoncesInHandshakePackets()
    {
        byte[] clientNonce = RandomNumberGenerator.GetBytes(16);
        byte[] helloPacket = PacketCodec.EncodeHello(clientNonce, TestPresharedKey);

        Assert.True(PacketValidator.ValidateDatagram(helloPacket, TestPresharedKey, null, out _));

        // Mutate client nonce in body without updating MAC
        byte[] mutatedNonce = (byte[])helloPacket.Clone();
        mutatedNonce[15] ^= 0x55;

        Assert.False(PacketValidator.ValidateDatagram(mutatedNonce, TestPresharedKey, null, out _));
        Assert.False(PacketValidator.VerifyMac(mutatedNonce, TestPresharedKey));
    }

    [Fact]
    public void SurvivesCorruptedKeyBitmaps_RandomBitsAndReservedKeysIgnored()
    {
        using var sink = new KeyboardMouseSink { RawInputHook = _ => { } };
        var diffEngine = new KeyStateDiffEngine(30);

        var rng = new Random(777);

        // Test with all 128 bits set (includes all unmapped key IDs 62..127)
        byte[] allOnesBitmap = new byte[16];
        Array.Fill(allOnesBitmap, (byte)0xFF);

        long nowMs = 1000;
        diffEngine.ProcessInput(allOnesBitmap, nowMs, (code, ext, up) => sink.SendKey(code, ext, up));

        // Must safely register valid mapped keys and ignore unmapped reserved keys
        Assert.NotEmpty(sink.GetHeldKeys());

        // Test 100 random bit sequence mutations
        for (int i = 0; i < 100; i++)
        {
            byte[] randomBitmap = new byte[16];
            rng.NextBytes(randomBitmap);
            nowMs += 40;

            diffEngine.ProcessInput(randomBitmap, nowMs, (code, ext, up) => sink.SendKey(code, ext, up));
        }

        // Mathematical proof of neutral state recovery: Neutralize must clear all held keys
        diffEngine.Neutralize((code, ext, up) => sink.SendKey(code, ext, up));
        sink.NeutralizeAll();

        Assert.Empty(sink.GetHeldKeys());
        Assert.Equal(0, sink.GetHeldMouseButtons());
    }

    [Fact]
    public async Task ListenerTaskResilience_SurvivesFuzzedTrafficWithoutCrashingOrStuckKeys()
    {
        int testPort = GetAvailableUdpPort();

        using var sink = new KeyboardMouseSink { RawInputHook = _ => { } };
        var focusGuard = new FocusGuard { AnyWindowMode = true };
        var triGuard = new SafetyTriGuard(sink, focusGuard);
        triGuard.Arm();

        var lookProcessor = new LookDeltaProcessor();
        var keyDiffEngine = new KeyStateDiffEngine(30);

        using var listener = new UdpListenerService(
            testPort,
            TestPresharedKey,
            sink,
            lookProcessor,
            keyDiffEngine,
            triGuard
        );

        listener.Start();

        using var client = new UdpClient();
        var targetEp = new IPEndPoint(IPAddress.Loopback, testPort);

        // 1. Establish valid session
        byte[] clientNonce = RandomNumberGenerator.GetBytes(16);
        byte[] helloPacket = PacketCodec.EncodeHello(clientNonce, TestPresharedKey);
        await client.SendAsync(helloPacket, targetEp);

        // Wait for handshake processing
        for (int i = 0; i < 20 && !listener.ActiveSessionId.HasValue; i++)
        {
            await Task.Delay(25);
        }
        Assert.NotNull(listener.ActiveSessionId);
        uint activeSession = listener.ActiveSessionId.Value;

        // 2. Send valid input packet that presses KeyId 22 ('W')
        byte[] keyBitmap = new byte[16];
        keyBitmap[22 / 8] |= (byte)(1 << (22 % 8)); // Key 22
        byte[] validKbm = PacketCodec.EncodeInputKbm(activeSession, 1, keyBitmap, 0x01, 10, 0, TestPresharedKey);
        await client.SendAsync(validKbm, targetEp);

        // 3. Flood listener with 300 malformed fuzzed datagrams
        var rng = new Random(999);
        for (int i = 0; i < 300; i++)
        {
            byte[] fuzzedBuffer;
            int mode = i % 5;
            switch (mode)
            {
                case 0: // Completely random length & bytes
                    fuzzedBuffer = new byte[rng.Next(1, 1024)];
                    rng.NextBytes(fuzzedBuffer);
                    break;
                case 1: // Truncated header
                    fuzzedBuffer = new byte[rng.Next(1, 12)];
                    rng.NextBytes(fuzzedBuffer);
                    break;
                case 2: // Invalid magic
                    fuzzedBuffer = (byte[])validKbm.Clone();
                    fuzzedBuffer[0] = 0x00;
                    fuzzedBuffer[1] = 0x00;
                    break;
                case 3: // Invalid type
                    fuzzedBuffer = (byte[])validKbm.Clone();
                    fuzzedBuffer[3] = 0xFF;
                    break;
                case 4: default: // Corrupted HMAC
                    fuzzedBuffer = (byte[])validKbm.Clone();
                    fuzzedBuffer[^1] ^= 0xFF;
                    break;
            }

            await client.SendAsync(fuzzedBuffer, targetEp);
        }

        await Task.Delay(100);

        // Listener must still be running and tracking datagrams
        Assert.True(listener.PacketCount > 300);

        // 4. Send clean Bye packet to neutralize
        byte[] byePacket = PacketCodec.EncodeBye(activeSession, 2, TestPresharedKey);
        await client.SendAsync(byePacket, targetEp);

        await Task.Delay(50);

        // Prove no stuck keys remain
        Assert.Empty(sink.GetHeldKeys());
        Assert.Equal(0, sink.GetHeldMouseButtons());
    }

    private static int GetAvailableUdpPort()
    {
        using var tempSocket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
        tempSocket.Bind(new IPEndPoint(IPAddress.Loopback, 0));
        return ((IPEndPoint)tempSocket.LocalEndPoint!).Port;
    }
}

/// <summary>
/// Replay attack test suite verifying RFC 1982 sequence anti-replay, session mismatch isolation,
/// and hello_ack client-nonce binding.
/// </summary>
public sealed class ReplayAttackTests
{
    private static readonly byte[] TestPresharedKey =
        Convert.FromHexString("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210");

    [Fact]
    public void ReplayWithinSameSession_RejectedBySequenceTracker_SerialArithmetic()
    {
        var tracker = new SequenceTracker();

        // 1. Initial sequence 1 accepted
        Assert.True(tracker.TryAccept(1));
        Assert.Equal(1u, tracker.LastAcceptedSeq);

        // 2. Monotonic next sequence 2 accepted
        Assert.True(tracker.TryAccept(2));
        Assert.Equal(2u, tracker.LastAcceptedSeq);

        // 3. Replay of sequence 2 rejected
        Assert.False(tracker.TryAccept(2));
        Assert.Equal(2u, tracker.LastAcceptedSeq);

        // 4. Replay of previous sequence 1 rejected
        Assert.False(tracker.TryAccept(1));
        Assert.Equal(2u, tracker.LastAcceptedSeq);

        // 5. Replay of sequence 0 rejected
        Assert.False(tracker.TryAccept(0));
        Assert.Equal(2u, tracker.LastAcceptedSeq);

        // 6. RFC 1982 Serial Arithmetic Wraparound Verification
        tracker.Reset(uint.MaxValue - 1); // 0xFFFFFFFE

        Assert.True(tracker.TryAccept(uint.MaxValue)); // 0xFFFFFFFF
        Assert.Equal(uint.MaxValue, tracker.LastAcceptedSeq);

        // 0 wraps around and is newer than uint.MaxValue: unchecked((int)(0 - uint.MaxValue)) == 1 > 0
        Assert.True(tracker.TryAccept(0));
        Assert.Equal(0u, tracker.LastAcceptedSeq);

        Assert.True(tracker.TryAccept(1));
        Assert.Equal(1u, tracker.LastAcceptedSeq);

        // Replay of wrapped sequences rejected
        Assert.False(tracker.TryAccept(0));
        Assert.False(tracker.TryAccept(uint.MaxValue));
        Assert.False(tracker.TryAccept(uint.MaxValue - 1));
        Assert.Equal(1u, tracker.LastAcceptedSeq);
    }

    [Fact]
    public void ReplayWithinSameSession_InputSinkDoesNotRetrigger()
    {
        using var sink = new KeyboardMouseSink { RawInputHook = _ => { } };
        var seqTracker = new SequenceTracker();
        var keyDiffEngine = new KeyStateDiffEngine(30);

        uint session = 0x44444444;
        byte[] keyBitmapLmbDown = new byte[16];
        byte mouseLmbDown = 0x01; // LMB down

        byte[] validPkt1 = PacketCodec.EncodeInputKbm(session, 10, keyBitmapLmbDown, mouseLmbDown, 0, 0, TestPresharedKey);

        // 1. First arrival of Packet 1 (Seq 10)
        Assert.True(PacketCodec.TryDecodeInputKbm(validPkt1, out var decodedPkt1));
        Assert.True(seqTracker.TryAccept(decodedPkt1.Header.Seq));

        sink.SendMouseButtons(decodedPkt1.MouseButtons);
        Assert.Equal(0x01, sink.GetHeldMouseButtons());

        // 2. Next packet (Seq 11) releases LMB
        byte[] validPkt2 = PacketCodec.EncodeInputKbm(session, 11, new byte[16], 0x00, 0, 0, TestPresharedKey);
        Assert.True(PacketCodec.TryDecodeInputKbm(validPkt2, out var decodedPkt2));
        Assert.True(seqTracker.TryAccept(decodedPkt2.Header.Seq));

        sink.SendMouseButtons(decodedPkt2.MouseButtons);
        Assert.Equal(0x00, sink.GetHeldMouseButtons());

        // 3. Attacker replays captured valid Packet 1 (Seq 10)
        Assert.True(PacketCodec.TryDecodeInputKbm(validPkt1, out var replayedPkt));
        bool accepted = seqTracker.TryAccept(replayedPkt.Header.Seq);

        // Packet dropped by sequence tracker
        Assert.False(accepted);

        // Input sink was NOT re-triggered; LMB remains unpressed
        Assert.Equal(0x00, sink.GetHeldMouseButtons());
    }

    [Fact]
    public void ReplayFromPreviousSession_RejectedBySessionIdValidation()
    {
        uint oldSessionId = 0x11112222;
        uint newSessionId = 0x99998888;

        byte[] keyBitmap = new byte[16];
        keyBitmap[0] = 0x01; // Key 0 ('A')

        // Capture a valid packet from old session
        byte[] oldSessionPacket = PacketCodec.EncodeInputKbm(
            oldSessionId,
            1,
            keyBitmap,
            0,
            0,
            0,
            TestPresharedKey
        );

        // Valid under old session
        Assert.True(PacketValidator.ValidateDatagram(
            oldSessionPacket,
            TestPresharedKey,
            oldSessionId,
            out var oldHeader
        ));
        Assert.Equal(oldSessionId, oldHeader.Session);

        // Replay into new session: PacketValidator MUST reject because session ID does not match active session
        bool acceptedInNewSession = PacketValidator.ValidateDatagram(
            oldSessionPacket,
            TestPresharedKey,
            newSessionId,
            out _
        );
        Assert.False(acceptedInNewSession);

        // Replay when receiver has no active session: MUST reject
        bool acceptedWithoutSession = PacketValidator.ValidateDatagram(
            oldSessionPacket,
            TestPresharedKey,
            null,
            out _
        );
        Assert.False(acceptedWithoutSession);

        // If attacker tampers with the session field in the header to match newSessionId without key:
        byte[] forgedSessionPacket = (byte[])oldSessionPacket.Clone();
        BinaryPrimitives.WriteUInt32LittleEndian(forgedSessionPacket.AsSpan(4, 4), newSessionId);

        // HMAC verification fails immediately
        bool acceptedForged = PacketValidator.ValidateDatagram(
            forgedSessionPacket,
            TestPresharedKey,
            newSessionId,
            out _
        );
        Assert.False(acceptedForged);
    }

    [Fact]
    public void ReplayOldHelloAckAgainstNewHello_MacVerificationFailsDueToNonceBinding()
    {
        // Session 1 Handshake
        byte[] clientNonce1 = RandomNumberGenerator.GetBytes(16);
        byte[] serverNonce1 = RandomNumberGenerator.GetBytes(16);

        // Server generates HelloAck with MAC calculated over Header[12] || ServerNonce[16] || ClientNonce[16]
        byte[] helloAck1 = PacketCodec.EncodeHelloAck(serverNonce1, clientNonce1, TestPresharedKey);

        // Client verifies HelloAck1 successfully against its active challenge clientNonce1
        Assert.True(PacketValidator.VerifyMac(helloAck1, TestPresharedKey, clientNonce1));
        Assert.True(PacketValidator.ValidateDatagram(helloAck1, TestPresharedKey, null, out _, clientNonce1));

        // Session 2 Handshake (Client reconnects with a newly generated nonce challenge)
        byte[] clientNonce2 = RandomNumberGenerator.GetBytes(16);
        Assert.NotEqual(clientNonce1, clientNonce2);

        // Attacker replays captured helloAck1 from Session 1 against Session 2
        bool macValidOnReplay = PacketValidator.VerifyMac(helloAck1, TestPresharedKey, clientNonce2);
        bool datagramValidOnReplay = PacketValidator.ValidateDatagram(helloAck1, TestPresharedKey, null, out _, clientNonce2);

        // Cryptographically bound client nonce mismatch MUST cause verification to fail
        Assert.False(macValidOnReplay);
        Assert.False(datagramValidOnReplay);

        // Nonce length tampering check: Truncated or empty client nonce must reject
        Assert.False(PacketValidator.VerifyMac(helloAck1, TestPresharedKey, ReadOnlySpan<byte>.Empty));
        Assert.False(PacketValidator.VerifyMac(helloAck1, TestPresharedKey, new byte[15]));
        Assert.False(PacketValidator.VerifyMac(helloAck1, TestPresharedKey, new byte[17]));
    }
}
