namespace TestSender;

public static class KeyMap
{
    private static readonly Dictionary<string, byte> NamesToId = new(StringComparer.OrdinalIgnoreCase)
    {
        { "A", 0 }, { "B", 1 }, { "C", 2 }, { "D", 3 }, { "E", 4 },
        { "F", 5 }, { "G", 6 }, { "H", 7 }, { "I", 8 }, { "J", 9 },
        { "K", 10 }, { "L", 11 }, { "M", 12 }, { "N", 13 }, { "O", 14 },
        { "P", 15 }, { "Q", 16 }, { "R", 17 }, { "S", 18 }, { "T", 19 },
        { "U", 20 }, { "V", 21 }, { "W", 22 }, { "X", 23 }, { "Y", 24 },
        { "Z", 25 },
        { "0", 26 }, { "1", 27 }, { "2", 28 }, { "3", 29 }, { "4", 30 },
        { "5", 31 }, { "6", 32 }, { "7", 33 }, { "8", 34 }, { "9", 35 },
        { "Space", 36 },
        { "Tab", 37 },
        { "Shift", 38 },
        { "Ctrl", 39 },
        { "Alt", 40 },
        { "Esc", 41 },
        { "Enter", 42 }
    };

    public static bool TryGetKeyId(string name, out byte id) => NamesToId.TryGetValue(name, out id);
}
