using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace TestSender;

public class Program
{
    private static readonly byte[] DefaultTestKey =
        Convert.FromHexString("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    public static async Task Main(string[] args)
    {
        string host = "127.0.0.1";
        int port = 47789;
        uint session = 0x12345678;
        string mode = "hold";
        string keysArg = "W";
        int durationMs = 3000;
        int sweepDx = 15;
        int sweepDy = 0;

        for (int i = 0; i < args.Length; i++)
        {
            switch (args[i].ToLowerInvariant())
            {
                case "--host": host = args[++i]; break;
                case "--port": port = int.Parse(args[++i]); break;
                case "--session": session = Convert.ToUInt32(args[++i], 16); break;
                case "--mode": mode = args[++i].ToLowerInvariant(); break;
                case "--keys": keysArg = args[++i]; break;
                case "--duration": durationMs = int.Parse(args[++i]); break;
                case "--dx": sweepDx = int.Parse(args[++i]); break;
                case "--dy": sweepDy = int.Parse(args[++i]); break;
            }
        }

        using var client = new UdpClient();
        var targetEndpoint = new IPEndPoint(IPAddress.Parse(host), port);
        Console.WriteLine($"[TestSender] Target: {host}:{port}, Session: 0x{session:X8}, Mode: {mode}");

        uint seq = 1;

        if (mode == "bye")
        {
            byte[] byePacket = Protocol.BuildPacket(Protocol.TypeBye, session, seq++, Array.Empty<byte>(), DefaultTestKey);
            await client.SendAsync(byePacket, targetEndpoint);
            Console.WriteLine("[TestSender] Sent Type 6 Bye packet.");
            return;
        }

        if (mode == "ping")
        {
            byte[] pingPayload = BitConverter.GetBytes((ulong)DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
            byte[] pingPacket = Protocol.BuildPacket(Protocol.TypePing, session, seq++, pingPayload, DefaultTestKey);
            await client.SendAsync(pingPacket, targetEndpoint);
            Console.WriteLine("[TestSender] Sent Type 2 Ping packet.");
            return;
        }

        // Prepare Key Bitmap & Mouse Flags
        byte[] keyBitmap = new byte[16];
        byte mouseButtons = 0;

        foreach (var keyName in keysArg.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            if (keyName.Equals("LMB", StringComparison.OrdinalIgnoreCase)) mouseButtons |= 0x01;
            else if (keyName.Equals("RMB", StringComparison.OrdinalIgnoreCase)) mouseButtons |= 0x02;
            else if (KeyMap.TryGetKeyId(keyName, out byte keyId))
            {
                keyBitmap[keyId / 8] |= (byte)(1 << (keyId % 8));
            }
            else
            {
                Console.WriteLine($"[TestSender] Warning: Unrecognized key '{keyName}' ignored.");
            }
        }

        int lookX = 0;
        int lookY = 0;
        var sw = Stopwatch.StartNew();

        if (mode == "tap")
        {
            Console.WriteLine($"[TestSender] Tapping '{keysArg}' with edge-latching (2 packets pressed, then released)...");
            
            // Send 2 consecutive packets with key DOWN (Plan Section 12: edge latching)
            for (int i = 0; i < 2; i++)
            {
                byte[] payload = Protocol.CreateInputKbmPayload(keyBitmap, mouseButtons, lookX, lookY);
                byte[] pkt = Protocol.BuildPacket(Protocol.TypeInputKbm, session, seq++, payload, DefaultTestKey);
                await client.SendAsync(pkt, targetEndpoint);
                await Task.Delay(8); // ~120 Hz tick
            }

            // Send neutral state
            byte[] neutral = Protocol.CreateInputKbmPayload(new byte[16], 0, lookX, lookY);
            byte[] neutralPkt = Protocol.BuildPacket(Protocol.TypeInputKbm, session, seq++, neutral, DefaultTestKey);
            await client.SendAsync(neutralPkt, targetEndpoint);

            Console.WriteLine("[TestSender] Tap completed and neutralized.");
            return;
        }

        if (mode == "sweep" || mode == "hold")
        {
            Console.WriteLine($"[TestSender] Running {mode} loop for {durationMs} ms at 120 Hz (~8.33 ms ticks)...");

            while (sw.ElapsedMilliseconds < durationMs)
            {
                if (mode == "sweep")
                {
                    lookX += sweepDx;
                    lookY += sweepDy;
                }

                byte[] payload = Protocol.CreateInputKbmPayload(keyBitmap, mouseButtons, lookX, lookY);
                byte[] pkt = Protocol.BuildPacket(Protocol.TypeInputKbm, session, seq++, payload, DefaultTestKey);
                await client.SendAsync(pkt, targetEndpoint);

                await Task.Delay(8);
            }

            // Send neutral state before terminating
            byte[] neutral = Protocol.CreateInputKbmPayload(new byte[16], 0, lookX, lookY);
            byte[] neutralPkt = Protocol.BuildPacket(Protocol.TypeInputKbm, session, seq++, neutral, DefaultTestKey);
            await client.SendAsync(neutralPkt, targetEndpoint);

            Console.WriteLine($"[TestSender] Finished sending {seq - 1} packets. Input neutralized.");
        }
    }
}
