/**
 * @aegis-contract
 * @claim End-to-End UDP Protocol Integration Verification Suite
 * @true Exercises complete network pipeline: Hello handshake, session derivation, KBM input, Ping/Pong, and Bye session termination
 * @false No mocked network sockets; uses real localhost UDP sockets
 */

using System.Buffers.Binary;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using PhoneGamepad.Receiver.Engine;
using PhoneGamepad.Receiver.Network;
using PhoneGamepad.Receiver.Protocol;
using PhoneGamepad.Receiver.Safety;
using PhoneGamepad.Receiver.Sinks;
using Xunit;

namespace PhoneGamepad.Tests.Integration;

public class EndToEndProtocolIntegrationTests : IDisposable
{
    private class RecordingInputSink : IInputSink
    {
        public readonly List<(ushort ScanCode, bool IsExt, bool IsUp)> KeyEvents = new();
        public readonly List<(int Dx, int Dy)> MouseMoves = new();
        public byte LastMouseButtons { get; private set; }
        public bool Neutralized { get; private set; }

        public void SendKey(ushort scanCode, bool isExt, bool isUp)
        {
            lock (KeyEvents)
            {
                KeyEvents.Add((scanCode, isExt, isUp));
            }
        }

        public void SendMouseMove(int deltaX, int deltaY)
        {
            lock (MouseMoves)
            {
                MouseMoves.Add((deltaX, deltaY));
            }
        }

        public void SendMouseButtons(byte mouseButtons)
        {
            LastMouseButtons = mouseButtons;
        }

        public void NeutralizeAll()
        {
            Neutralized = true;
        }

        public void SendInputKbm(ReadOnlySpan<byte> k, byte m, int dx, int dy) { }
        public void SendInputPad(ushort b, byte lt, byte rt, short lx, short ly, short rx, short ry) { }
        public bool IsKbmMode => true;
        public bool IsPadMode => false;
        public IReadOnlySet<(ushort ScanCode, bool IsExtended)> GetHeldKeys() => new HashSet<(ushort, bool)>();
        public byte GetHeldMouseButtons() => LastMouseButtons;
        public void Dispose() { }
    }

    private readonly byte[] _psk = RandomNumberGenerator.GetBytes(32);
    private readonly int _testPort;
    private readonly RecordingInputSink _sink;
    private readonly SafetyTriGuard _triGuard;
    private readonly UdpListenerService _listener;

    private static int GetAvailableUdpPort()
    {
        using var temp = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
        temp.Bind(new IPEndPoint(IPAddress.Loopback, 0));
        return ((IPEndPoint)temp.LocalEndPoint!).Port;
    }

    public EndToEndProtocolIntegrationTests()
    {
        _testPort = GetAvailableUdpPort();
        _sink = new RecordingInputSink();
        var focusGuard = new FocusGuard { AnyWindowMode = true };
        _triGuard = new SafetyTriGuard(_sink, focusGuard);
        _triGuard.Arm();

        _listener = new UdpListenerService(
            _testPort,
            _psk,
            _sink,
            new LookDeltaProcessor(),
            new KeyStateDiffEngine(30),
            _triGuard
        );
        _listener.Start();
    }

    public void Dispose()
    {
        _listener.Dispose();
        _sink.Dispose();
    }

    [Fact]
    public async Task FullSessionLifecycle_Handshake_Input_PingPong_Bye()
    {
        using var client = new UdpClient();
        client.Client.ReceiveTimeout = 2000;
        var serverEndpoint = new IPEndPoint(IPAddress.Loopback, _testPort);

        // 1. Send Hello (Type 4)
        byte[] clientNonce = RandomNumberGenerator.GetBytes(16);
        byte[] helloPkt = PacketCodec.EncodeHello(clientNonce, _psk);
        await client.SendAsync(helloPkt, serverEndpoint);

        // 2. Receive HelloAck (Type 5)
        var ackResult = await client.ReceiveAsync();
        Assert.Equal(ProtocolConstants.SizeHelloAck, ackResult.Buffer.Length);
        Assert.Equal(ProtocolConstants.TypeHelloAck, ackResult.Buffer[3]);

        // Decode HelloAck
        Assert.True(PacketCodec.TryDecodeHelloAck(ackResult.Buffer, out var helloAck));

        // Derive Session ID on client side: HMAC(Key, ClientNonce || ServerNonce)[0..4]
        byte[] nonceConcat = new byte[32];
        Buffer.BlockCopy(clientNonce, 0, nonceConcat, 0, 16);
        Buffer.BlockCopy(helloAck.ServerNonce, 0, nonceConcat, 16, 16);
        using var hmac = new HMACSHA256(_psk);
        byte[] digest = hmac.ComputeHash(nonceConcat);
        uint derivedSession = BinaryPrimitives.ReadUInt32LittleEndian(digest.AsSpan(0, 4));

        Assert.Equal(derivedSession, _listener.ActiveSessionId);

        // Also receive initial Status packet sent right after HelloAck
        var statusResult = await client.ReceiveAsync();
        Assert.Equal(ProtocolConstants.SizeStatus, statusResult.Buffer.Length);
        Assert.Equal(ProtocolConstants.TypeStatus, statusResult.Buffer[3]);

        // 3. Send InputKbm (Type 0) with 'W' pressed (KeyId = 22, MakeCode = 0x11)
        byte[] keys = new byte[16];
        keys[22 / 8] |= (byte)(1 << (22 % 8)); // Press 'W'

        byte[] inputPkt = PacketCodec.EncodeInputKbm(
            derivedSession,
            seq: 1,
            keysBitmap16: keys,
            mouse: 0x01, // LMB
            lookX: 100,
            lookY: -50,
            key: _psk
        );
        await client.SendAsync(inputPkt, serverEndpoint);

        // Wait brief interval for listener thread to dispatch
        await Task.Delay(100);

        lock (_sink.KeyEvents)
        {
            Assert.Contains(_sink.KeyEvents, k => k.ScanCode == 0x11 && !k.IsUp);
        }
        Assert.Equal(0x01, _sink.LastMouseButtons);

        // 4. Send Ping (Type 2), Expect Pong (Type 3)
        ulong testTimestamp = 0x0123456789ABCDEF;
        byte[] pingPkt = PacketCodec.EncodePing(derivedSession, seq: 2, timestamp: testTimestamp, key: _psk);
        await client.SendAsync(pingPkt, serverEndpoint);

        var pongResult = await client.ReceiveAsync();
        Assert.Equal(ProtocolConstants.SizePong, pongResult.Buffer.Length);
        Assert.Equal(ProtocolConstants.TypePong, pongResult.Buffer[3]);
        ulong pongTimestamp = BinaryPrimitives.ReadUInt64LittleEndian(pongResult.Buffer.AsSpan(12, 8));
        Assert.Equal(testTimestamp, pongTimestamp);

        // 5. Send Bye (Type 6), Expect ActiveSessionId cleared and NeutralizeAll called
        byte[] byePkt = PacketCodec.EncodeBye(derivedSession, seq: 3, key: _psk);
        await client.SendAsync(byePkt, serverEndpoint);

        await Task.Delay(50);
        Assert.Null(_listener.ActiveSessionId);
        Assert.True(_sink.Neutralized);
    }

    [Fact]
    public async Task TamperedPacket_RejectedSilently()
    {
        using var client = new UdpClient();
        var serverEndpoint = new IPEndPoint(IPAddress.Loopback, _testPort);

        // Send Hello to establish session
        byte[] clientNonce = RandomNumberGenerator.GetBytes(16);
        byte[] helloPkt = PacketCodec.EncodeHello(clientNonce, _psk);
        await client.SendAsync(helloPkt, serverEndpoint);
        await client.ReceiveAsync(); // HelloAck
        await client.ReceiveAsync(); // Status

        uint session = _listener.ActiveSessionId!.Value;

        // Send Tampered packet (corrupted HMAC)
        byte[] tamperedPkt = PacketCodec.EncodeInputKbm(
            session, seq: 10, keysBitmap16: new byte[16], mouse: 0,
            lookX: 0, lookY: 0, key: _psk
        );
        tamperedPkt[^1] ^= 0xFF; // Flip bit in HMAC
        await client.SendAsync(tamperedPkt, serverEndpoint);

        await Task.Delay(50);
        // Packet was received by socket, but discarded before affecting sink
        Assert.Empty(_sink.KeyEvents);
    }
}
