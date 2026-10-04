/**
 * @aegis-contract
 * @claim Safety Tri-Guard Coordinator: Watchdog, Focus Guard, and Emergency Kill-Switch
 * @true Watchdog neutralizes at 500ms inactivity, FocusGuard neutralizes on focus loss, KillSwitch toggles armed state
 * @false Permits no stuck keys under any disconnection, focus transition, or operator emergency
 */

using System.Diagnostics;
using PhoneGamepad.Receiver.Sinks;

namespace PhoneGamepad.Receiver.Safety;

public sealed class SafetyTriGuard : IDisposable
{
    private readonly IInputSink _sink;
    private readonly FocusGuard _focusGuard;
    private readonly Stopwatch _watchdogStopwatch = new();
    private readonly object _lock = new();

    public bool IsArmed { get; private set; } = false;
    public bool IsTargetFocused { get; private set; } = false;
    public bool IsElevationWarning { get; private set; } = false;
    public string CurrentProcessName { get; private set; } = "None";

    public event Action<bool>? ArmedChanged;
    public event Action<bool>? FocusChanged;
    public event Action? WatchdogTimeoutFired;

    public SafetyTriGuard(IInputSink sink, FocusGuard focusGuard)
    {
        _sink = sink;
        _focusGuard = focusGuard;
    }

    public void Arm()
    {
        lock (_lock)
        {
            IsArmed = true;
            _watchdogStopwatch.Restart();
            PollTick(); // Evaluate focus and state immediately upon arming
            ArmedChanged?.Invoke(true);
        }
    }

    public void Disarm()
    {
        lock (_lock)
        {
            IsArmed = false;
            _watchdogStopwatch.Stop();
            _sink.NeutralizeAll();
            ArmedChanged?.Invoke(false);
        }
    }

    public void ToggleArmed()
    {
        if (IsArmed) Disarm();
        else Arm();
    }

    public void NotifyPacketReceived()
    {
        lock (_lock)
        {
            _watchdogStopwatch.Restart();
        }
    }

    public void PollTick()
    {
        lock (_lock)
        {
            // 1. Focus Guard Check
            bool focused = _focusGuard.IsTargetInFocus(out string procName, out bool elevated);
            CurrentProcessName = procName;
            IsElevationWarning = elevated;

            if (focused != IsTargetFocused)
            {
                IsTargetFocused = focused;
                FocusChanged?.Invoke(focused);

                if (!focused)
                {
                    _sink.NeutralizeAll();
                }
            }

            // 2. Watchdog Check (500 ms)
            if (IsArmed && _watchdogStopwatch.IsRunning && _watchdogStopwatch.ElapsedMilliseconds > 500)
            {
                _sink.NeutralizeAll();
                WatchdogTimeoutFired?.Invoke();
                _watchdogStopwatch.Restart(); // Wait for next packet before neutral state clears
            }
        }
    }

    public bool CanInjectInput()
    {
        lock (_lock)
        {
            return IsArmed && IsTargetFocused && !IsElevationWarning;
        }
    }

    public void Dispose()
    {
        Disarm();
    }
}
