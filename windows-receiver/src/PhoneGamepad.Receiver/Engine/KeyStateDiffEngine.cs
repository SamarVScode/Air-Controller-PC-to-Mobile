/**
 * @aegis-contract
 * @claim Key State Diff Engine with 30ms Minimum Hold Time Enforcement
 * @true Computes down/up diffs, latches key-up events until 30ms hold time is reached
 * @true Provides emergency immediate neutralization bypassing hold timers
 * @false Never loses brief taps or short-circuits hold times outside emergency neutralization
 */

using PhoneGamepad.Receiver.Protocol;

namespace PhoneGamepad.Receiver.Engine;

public sealed class KeyStateDiffEngine
{
    private readonly byte[] _heldBitmap = new byte[16];
    private readonly Dictionary<byte, long> _keyDownTimestamps = new();
    private readonly Dictionary<byte, long> _pendingKeyUps = new();
    private readonly int _minHoldTimeMs;

    public KeyStateDiffEngine(int minHoldTimeMs = 30)
    {
        _minHoldTimeMs = Math.Clamp(minHoldTimeMs, 0, 50);
    }

    public void ProcessInput(
        ReadOnlySpan<byte> newBitmap16,
        long nowTimestampMs,
        Action<ushort, bool, bool> emitKeyEvent
    )
    {
        // 1. Process pending key-ups that reached minimum hold duration
        var keysToRelease = new List<byte>();
        foreach (var (keyId, releaseAfterMs) in _pendingKeyUps)
        {
            if (nowTimestampMs >= releaseAfterMs)
            {
                keysToRelease.Add(keyId);
            }
        }

        foreach (var keyId in keysToRelease)
        {
            _pendingKeyUps.Remove(keyId);
            ClearBit(_heldBitmap, keyId);
            _keyDownTimestamps.Remove(keyId);

            if (KeyMap.TryGetMapping(keyId, out var mapping))
            {
                emitKeyEvent(mapping.MakeCode, mapping.IsExtended, true);
            }
        }

        // 2. Diff newly received bitmap against current physical state
        for (byte keyId = 0; keyId < 62; keyId++)
        {
            bool isDownNow = IsBitSet(newBitmap16, keyId);
            bool wasDown = IsBitSet(_heldBitmap, keyId);

            if (isDownNow && !wasDown)
            {
                // New Key Down
                _pendingKeyUps.Remove(keyId);
                SetBit(_heldBitmap, keyId);
                _keyDownTimestamps[keyId] = nowTimestampMs;

                if (KeyMap.TryGetMapping(keyId, out var mapping))
                {
                    emitKeyEvent(mapping.MakeCode, mapping.IsExtended, false);
                }
            }
            else if (!isDownNow && wasDown && !_pendingKeyUps.ContainsKey(keyId))
            {
                // Key Up requested
                long pressedAt = _keyDownTimestamps.TryGetValue(keyId, out var ts) ? ts : nowTimestampMs;
                long elapsed = nowTimestampMs - pressedAt;

                if (elapsed >= _minHoldTimeMs)
                {
                    ClearBit(_heldBitmap, keyId);
                    _keyDownTimestamps.Remove(keyId);
                    if (KeyMap.TryGetMapping(keyId, out var mapping))
                    {
                        emitKeyEvent(mapping.MakeCode, mapping.IsExtended, true);
                    }
                }
                else
                {
                    // Delay release until 30ms hold time is reached
                    _pendingKeyUps[keyId] = pressedAt + _minHoldTimeMs;
                }
            }
        }
    }

    public void Tick(long nowTimestampMs, Action<ushort, bool, bool> emitKeyEvent)
    {
        var keysToRelease = new List<byte>();
        foreach (var (keyId, releaseAfterMs) in _pendingKeyUps)
        {
            if (nowTimestampMs >= releaseAfterMs)
            {
                keysToRelease.Add(keyId);
            }
        }

        foreach (var keyId in keysToRelease)
        {
            _pendingKeyUps.Remove(keyId);
            ClearBit(_heldBitmap, keyId);
            _keyDownTimestamps.Remove(keyId);

            if (KeyMap.TryGetMapping(keyId, out var mapping))
            {
                emitKeyEvent(mapping.MakeCode, mapping.IsExtended, true);
            }
        }
    }

    public void Neutralize(Action<ushort, bool, bool> emitKeyEvent)
    {
        _pendingKeyUps.Clear();
        _keyDownTimestamps.Clear();

        for (byte keyId = 0; keyId < 62; keyId++)
        {
            if (IsBitSet(_heldBitmap, keyId))
            {
                ClearBit(_heldBitmap, keyId);
                if (KeyMap.TryGetMapping(keyId, out var mapping))
                {
                    emitKeyEvent(mapping.MakeCode, mapping.IsExtended, true);
                }
            }
        }
    }

    private static bool IsBitSet(ReadOnlySpan<byte> bitmap, byte keyId) => (bitmap[keyId / 8] & (1 << (keyId % 8))) != 0;
    private static void SetBit(Span<byte> bitmap, byte keyId) => bitmap[keyId / 8] |= (byte)(1 << (keyId % 8));
    private static void ClearBit(Span<byte> bitmap, byte keyId) => bitmap[keyId / 8] &= (byte)~(1 << (keyId % 8));
}
