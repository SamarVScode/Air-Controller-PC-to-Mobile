/**
 * @aegis-contract
 * @claim Complete authoritative KeyId 0..127 to PS/2 Set 1 Scan Code Mapping
 * @true Accurately maps letters, numbers, functional and modifier keys, with ExtendedKey flags for arrow keys
 * @false Contains no renumbered keys or missing mappings specified in docs/keys.md
 */

namespace PhoneGamepad.Receiver.Protocol;

public readonly record struct KeyMapping(byte KeyId, string Name, ushort MakeCode, bool IsExtended);

public static class KeyMap
{
    private static readonly KeyMapping?[] Table = new KeyMapping?[128];
    private static readonly Dictionary<string, byte> NameToId = new(StringComparer.OrdinalIgnoreCase);

    static KeyMap()
    {
        // Letters (0..25) -> A..Z
        Register(0, "A", 0x1E);
        Register(1, "B", 0x30);
        Register(2, "C", 0x2E);
        Register(3, "D", 0x20);
        Register(4, "E", 0x12);
        Register(5, "F", 0x21);
        Register(6, "G", 0x22);
        Register(7, "H", 0x23);
        Register(8, "I", 0x17);
        Register(9, "J", 0x24);
        Register(10, "K", 0x25);
        Register(11, "L", 0x26);
        Register(12, "M", 0x32);
        Register(13, "N", 0x31);
        Register(14, "O", 0x18);
        Register(15, "P", 0x19);
        Register(16, "Q", 0x10);
        Register(17, "R", 0x13);
        Register(18, "S", 0x1F);
        Register(19, "T", 0x14);
        Register(20, "U", 0x16);
        Register(21, "V", 0x2F);
        Register(22, "W", 0x11);
        Register(23, "X", 0x2D);
        Register(24, "Y", 0x15);
        Register(25, "Z", 0x2C);

        // Digits (26..35) -> 0..9
        Register(26, "0", 0x0B);
        Register(27, "1", 0x02);
        Register(28, "2", 0x03);
        Register(29, "3", 0x04);
        Register(30, "4", 0x05);
        Register(31, "5", 0x06);
        Register(32, "6", 0x07);
        Register(33, "7", 0x08);
        Register(34, "8", 0x09);
        Register(35, "9", 0x0A);

        // Control & Whitespace
        Register(36, "Space", 0x39);
        Register(37, "Tab", 0x0F);
        Register(38, "LeftShift", 0x2A);
        Register(39, "LeftCtrl", 0x1D);
        Register(40, "LeftAlt", 0x38);
        Register(41, "Escape", 0x01);
        Register(42, "Enter", 0x1C);
        Register(43, "CapsLock", 0x3A);

        // Function keys (44..55) -> F1..F12
        Register(44, "F1", 0x3B);
        Register(45, "F2", 0x3C);
        Register(46, "F3", 0x3D);
        Register(47, "F4", 0x3E);
        Register(48, "F5", 0x3F);
        Register(49, "F6", 0x40);
        Register(50, "F7", 0x41);
        Register(51, "F8", 0x42);
        Register(52, "F9", 0x43);
        Register(53, "F10", 0x44);
        Register(54, "F11", 0x57);
        Register(55, "F12", 0x58);

        // Arrow keys (Extended)
        Register(56, "UpArrow", 0x48, isExtended: true);
        Register(57, "DownArrow", 0x50, isExtended: true);
        Register(58, "LeftArrow", 0x4B, isExtended: true);
        Register(59, "RightArrow", 0x4D, isExtended: true);

        // Navigation / Punctuation
        Register(60, "Backspace", 0x0E);
        Register(61, "Tilde", 0x29);
    }

    private static void Register(byte keyId, string name, ushort makeCode, bool isExtended = false)
    {
        var mapping = new KeyMapping(keyId, name, makeCode, isExtended);
        Table[keyId] = mapping;
        NameToId[name] = keyId;
    }

    public static bool TryGetMapping(byte keyId, out KeyMapping mapping)
    {
        if (keyId < Table.Length && Table[keyId].HasValue)
        {
            mapping = Table[keyId]!.Value;
            return true;
        }

        mapping = default;
        return false;
    }

    public static bool TryGetKeyId(string name, out byte keyId)
    {
        return NameToId.TryGetValue(name, out keyId);
    }
}
