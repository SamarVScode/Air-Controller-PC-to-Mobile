/**
 * @aegis-contract
 * @claim Windows DPAPI Key Persistence Service
 * @true Protects 32-byte pre-shared secret at rest via DPAPI CurrentUser scope
 * @false Never writes cleartext keys to disk
 */

using System.IO;
using System.Security.Cryptography;

namespace PhoneGamepad.Receiver.Security;

public static class DpapiStorage
{
    private static readonly string StorageDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "PhoneAsGamepad"
    );
    private static readonly string StorageFile = Path.Combine(StorageDir, "receiver.dat");
    private static readonly byte[] Entropy = "PhoneAsGamepad_Receiver_Salt_v1"u8.ToArray();

    public static void SaveKey(byte[] keyBytes)
    {
        if (keyBytes.Length != 32)
            throw new ArgumentException("Pre-shared key must be exactly 32 bytes.", nameof(keyBytes));

        Directory.CreateDirectory(StorageDir);
        byte[] encrypted = ProtectedData.Protect(keyBytes, Entropy, DataProtectionScope.CurrentUser);
        File.WriteAllBytes(StorageFile, encrypted);
    }

    public static byte[]? LoadKey()
    {
        if (!File.Exists(StorageFile)) return null;

        try
        {
            byte[] encrypted = File.ReadAllBytes(StorageFile);
            byte[] decrypted = ProtectedData.Unprotect(encrypted, Entropy, DataProtectionScope.CurrentUser);
            return decrypted.Length == 32 ? decrypted : null;
        }
        catch
        {
            return null;
        }
    }

    public static byte[] GetOrCreateKey()
    {
        var key = LoadKey();
        if (key != null) return key;

        key = RandomNumberGenerator.GetBytes(32);
        SaveKey(key);
        return key;
    }
}
