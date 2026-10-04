/**
 * @aegis-contract
 * @claim RFC 1982 Serial Number Arithmetic for 32-bit sequence numbers
 * @true Handles wrapping around uint.MaxValue gracefully via unchecked subtraction
 * @false Avoids direct comparison relational operators (seq > lastSeq)
 */

namespace PhoneGamepad.Receiver.Security;

public static class SequenceValidator
{
    public static bool IsNewer(uint candidateSeq, uint lastAcceptedSeq)
    {
        return unchecked((int)(candidateSeq - lastAcceptedSeq)) > 0;
    }
}

public sealed class SequenceTracker
{
    public uint LastAcceptedSeq { get; private set; }
    public bool HasReceivedInitial { get; private set; }

    public void Reset(uint initialSeq = 0)
    {
        LastAcceptedSeq = initialSeq;
        HasReceivedInitial = false;
    }

    public bool TryAccept(uint candidateSeq)
    {
        if (!HasReceivedInitial)
        {
            LastAcceptedSeq = candidateSeq;
            HasReceivedInitial = true;
            return true;
        }

        if (SequenceValidator.IsNewer(candidateSeq, LastAcceptedSeq))
        {
            LastAcceptedSeq = candidateSeq;
            return true;
        }

        return false;
    }
}
