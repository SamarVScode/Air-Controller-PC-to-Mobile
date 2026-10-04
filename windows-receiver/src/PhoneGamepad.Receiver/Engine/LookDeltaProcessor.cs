/**
 * @aegis-contract
 * @claim Cumulative Look Counter Delta Processor with 250ms Silence Re-baselining
 * @true Handles 32-bit signed wrapping, clamps to [-4000, 4000], accumulates fractional sub-pixel remainders
 * @false Never teleports the camera after reconnect or network gap > 250ms
 */

namespace PhoneGamepad.Receiver.Engine;

public sealed class LookDeltaProcessor
{
    private int _previousLookX;
    private int _previousLookY;
    private bool _hasBaseline;
    private long _lastPacketTimeMs;

    private float _remainderX;
    private float _remainderY;

    public float LookScale { get; set; } = 1.0f;
    public event Action<int, int>? ClampingWarningLogged;

    public void Reset()
    {
        _hasBaseline = false;
        _remainderX = 0f;
        _remainderY = 0f;
    }

    public (int DeltaX, int DeltaY) ProcessLook(int currentLookX, int currentLookY, long nowMs)
    {
        // 1. Initial baseline rule
        if (!_hasBaseline)
        {
            _previousLookX = currentLookX;
            _previousLookY = currentLookY;
            _hasBaseline = true;
            _lastPacketTimeMs = nowMs;
            return (0, 0);
        }

        // 2. 250 ms Silence Re-baseline rule
        if (nowMs - _lastPacketTimeMs > 250)
        {
            _previousLookX = currentLookX;
            _previousLookY = currentLookY;
            _lastPacketTimeMs = nowMs;
            return (0, 0);
        }

        _lastPacketTimeMs = nowMs;

        // 3. Wraparound-safe delta
        int rawDx = unchecked(currentLookX - _previousLookX);
        int rawDy = unchecked(currentLookY - _previousLookY);

        _previousLookX = currentLookX;
        _previousLookY = currentLookY;

        // 4. Clamping safety limits [-4000, 4000]
        int clampedDx = Math.Clamp(rawDx, -4000, 4000);
        int clampedDy = Math.Clamp(rawDy, -4000, 4000);

        if (clampedDx != rawDx || clampedDy != rawDy)
        {
            ClampingWarningLogged?.Invoke(rawDx, rawDy);
        }

        // 5. PC Sensitivity Scaling & Fractional Remainder Carry
        float totalX = (clampedDx * LookScale) + _remainderX;
        float totalY = (clampedDy * LookScale) + _remainderY;

        int finalDx = (int)MathF.Round(totalX);
        int finalDy = (int)MathF.Round(totalY);

        _remainderX = totalX - finalDx;
        _remainderY = totalY - finalDy;

        return (finalDx, finalDy);
    }
}
