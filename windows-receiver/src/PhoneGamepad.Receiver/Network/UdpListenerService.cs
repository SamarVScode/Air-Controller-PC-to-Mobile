/**
 * @aegis-contract
 * @claim Asynchronous High-Performance UDP Listener and Session Dispatcher
 * @true Binds to 0.0.0.0:47789, rate-limits at 1000 pps, dispatches KBM, handshake, ping/pong, and status
 * @false Never leaks socket descriptors or stalls on slow packet processing
 */

using System.Buffers.Binary;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using PhoneGamepad.Receiver.Engine;
using PhoneGamepad.Receiver.Protocol;
using PhoneGamepad.Receiver.Safety;
using PhoneGamepad.Receiver.Security;
using PhoneGamepad.Receiver.Sinks;

namespace PhoneGamepad.Receiver.Network;

public sealed class UdpListenerService : IDisposable
{
    private readonly int _port;
    private readonly byte[] _presharedKey;
    private readonly IInputSink _sink;
    private readonly LookDeltaProcessor _lookProcessor;
    private readonly KeyStateDiffEngine _keyDiffEngine;
    private readonly SafetyTriGuard _triGuard;

    private Socket? _socket;
    private CancellationTokenSource? _cts;
    private Task? _listenerTask;
    private Task? _housekeepingTask;

    private uint? _activeSessionId;
    private IPEndPoint? _activeClientEndpoint;
    private readonly SequenceTracker _seqTracker = new();
    private byte[]? _lastClientNonce;

    private long _packetsReceivedInCurrentSecond = 0;
    private long _currentSecondWindowStart = 0;

    public uint? ActiveSessionId => _activeSessionId;
    public IPEndPoint? ActiveClientEndpoint => _activeClientEndpoint;
    public long PacketCount { get; private set; }

    public UdpListenerService(
        int port,
        byte[] presharedKey,
        IInputSink sink,
        LookDeltaProcessor lookProcessor,
        KeyStateDiffEngine keyDiffEngine,
        SafetyTriGuard triGuard
    )
    {
        _port = port;
        _presharedKey = presharedKey;
        _sink = sink;
        _lookProcessor = lookProcessor;
        _keyDiffEngine = keyDiffEngine;
        _triGuard = triGuard;
    }

    public void Start()
    {
        _socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp)
        {
            ReceiveBufferSize = 256 * 1024,
            SendBufferSize = 64 * 1024
        };
        _socket.Bind(new IPEndPoint(IPAddress.Any, _port));

        _cts = new CancellationTokenSource();
        _listenerTask = Task.Run(() => ListenLoopAsync(_cts.Token));
        _housekeepingTask = Task.Run(() => HousekeepingLoopAsync(_cts.Token));
    }

    private async Task ListenLoopAsync(CancellationToken ct)
    {
        byte[] receiveBuffer = new byte[2048];
        EndPoint remoteEp = new IPEndPoint(IPAddress.Any, 0);

        while (!ct.IsCancellationRequested && _socket != null)
        {
            try
            {
                var result = await _socket.ReceiveFromAsync(receiveBuffer, SocketFlags.None, remoteEp);
                long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

                // 1. Rate Limiting (< 1000 pps)
                if (nowMs - _currentSecondWindowStart >= 1000)
                {
                    _currentSecondWindowStart = nowMs;
                    _packetsReceivedInCurrentSecond = 0;
                }

                if (++_packetsReceivedInCurrentSecond > 1000)
                {
                    continue; // Drop abusive traffic
                }

                PacketCount++;
                ProcessDatagram(receiveBuffer.AsSpan(0, result.ReceivedBytes), (IPEndPoint)result.RemoteEndPoint, nowMs);
            }
            catch (SocketException) when (ct.IsCancellationRequested) { break; }
            catch (ObjectDisposedException) { break; }
            catch { /* Resilient against malformed datagram processing errors */ }
        }
    }

    private void ProcessDatagram(ReadOnlySpan<byte> datagram, IPEndPoint remoteEp, long nowMs)
    {
        if (datagram.Length < ProtocolConstants.HeaderSize + ProtocolConstants.MacSize) return;

        byte type = datagram[3];

        // 1. Handshake Initiation: Type 4 (Hello)
        if (type == ProtocolConstants.TypeHello)
        {
            if (PacketValidator.ValidateDatagram(datagram, _presharedKey, null, out var header))
            {
                HandleHello(datagram, remoteEp);
            }
            return;
        }

        // 2. Stream Packets: Type 0, 2, 6
        if (!PacketValidator.ValidateDatagram(datagram, _presharedKey, _activeSessionId, out var streamHeader))
        {
            return;
        }

        // 3. RFC 1982 Sequence Validation
        if (!_seqTracker.TryAccept(streamHeader.Seq))
        {
            return;
        }

        _triGuard.NotifyPacketReceived();

        switch (type)
        {
            case ProtocolConstants.TypeInputKbm:
                HandleInputKbm(datagram, nowMs);
                break;

            case ProtocolConstants.TypePing:
                HandlePing(datagram, remoteEp);
                break;

            case ProtocolConstants.TypeBye:
                HandleBye();
                break;
        }
    }

    private void HandleHello(ReadOnlySpan<byte> datagram, IPEndPoint remoteEp)
    {
        if (!PacketCodec.TryDecodeHello(datagram, out var hello)) return;

        // Session Preemption Rule: Immediate Neutralize
        _sink.NeutralizeAll();
        _lookProcessor.Reset();
        _seqTracker.Reset(0);

        _lastClientNonce = hello.ClientNonce;
        byte[] serverNonce = RandomNumberGenerator.GetBytes(16);

        // Derive 4-byte session ID = HMAC(Key, ClientNonce || ServerNonce)[0..3]
        byte[] nonceConcat = new byte[32];
        Buffer.BlockCopy(hello.ClientNonce, 0, nonceConcat, 0, 16);
        Buffer.BlockCopy(serverNonce, 0, nonceConcat, 16, 16);

        using var hmac = new HMACSHA256(_presharedKey);
        byte[] digest = hmac.ComputeHash(nonceConcat);
        uint derivedSessionId = BinaryPrimitives.ReadUInt32LittleEndian(digest.AsSpan(0, 4));

        _activeSessionId = derivedSessionId;
        _activeClientEndpoint = remoteEp;

        // Send HelloAck (Type 5)
        byte[] ackPacket = PacketCodec.EncodeHelloAck(serverNonce, hello.ClientNonce, _presharedKey);
        _socket?.SendTo(ackPacket, remoteEp);

        // Broadcast initial Status
        SendStatusPacket();
    }

    private void HandleInputKbm(ReadOnlySpan<byte> datagram, long nowMs)
    {
        if (!PacketCodec.TryDecodeInputKbm(datagram, out var pkt)) return;

        if (_triGuard.CanInjectInput())
        {
            // Cumulative Look
            var (dx, dy) = _lookProcessor.ProcessLook(pkt.LookX, pkt.LookY, nowMs);

            // Key-State Diff with 30ms Hold Time
            _keyDiffEngine.ProcessInput(pkt.KeysBitmap, nowMs, (scanCode, isExt, isUp) =>
            {
                _sink.SendKey(scanCode, isExt, isUp);
            });

            // Mouse Buttons & Movement
            _sink.SendMouseButtons(pkt.MouseButtons);
            if (dx != 0 || dy != 0)
            {
                _sink.SendMouseMove(dx, dy);
            }
        }
    }

    private void HandlePing(ReadOnlySpan<byte> datagram, IPEndPoint remoteEp)
    {
        if (datagram.Length != ProtocolConstants.SizePing) return;
        ulong timestamp = BinaryPrimitives.ReadUInt64LittleEndian(datagram.Slice(12, 8));

        byte[] pongPacket = PacketCodec.EncodePong(_activeSessionId ?? 0, 0, timestamp, _presharedKey);
        _socket?.SendTo(pongPacket, remoteEp);
    }

    private void HandleBye()
    {
        _sink.NeutralizeAll();
        _activeSessionId = null;
        _activeClientEndpoint = null;
        _seqTracker.Reset();
    }

    public void SendStatusPacket()
    {
        if (_activeSessionId == null || _activeClientEndpoint == null || _socket == null) return;

        byte flags = 0;
        if (_triGuard.IsArmed) flags |= 0x01;
        if (_triGuard.IsTargetFocused) flags |= 0x02;
        if (_sink.IsKbmMode) flags |= 0x04;
        if (_sink.IsPadMode) flags |= 0x08;

        byte[] statusPkt = PacketCodec.EncodeStatus(_activeSessionId.Value, 0, flags, _presharedKey);
        try
        {
            _socket.SendTo(statusPkt, _activeClientEndpoint);
        }
        catch { }
    }

    private async Task HousekeepingLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                await Task.Delay(16, ct); // ~60 Hz safety and hold-time tick
                long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

                _triGuard.PollTick();

                // KeyStateDiffEngine timer tick for expired 30ms holds
                _keyDiffEngine.Tick(nowMs, (scanCode, isExt, isUp) =>
                {
                    _sink.SendKey(scanCode, isExt, isUp);
                });
            }
            catch (TaskCanceledException) { break; }
            catch { }
        }
    }

    public void Dispose()
    {
        _cts?.Cancel();
        _socket?.Dispose();
        _cts?.Dispose();
    }
}
