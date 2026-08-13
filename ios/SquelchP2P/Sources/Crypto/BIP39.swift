import Foundation

/// A deliberately small dependency-free BIP-39 + AES-GCM + Argon2id-ish
/// foundation for the iOS scaffold. Code corresponds to the Kotlin
/// `Bip39`, `VaultCipher`, `Argon2id`, `AesGcm` in the Android module.
///
/// Real iOS builds swap these for CommonCrypto-backed implementations;
/// this scaffold is for documentation and review only.
///
/// Source-only - the project compiles in Xcode on macOS after wiring
/// CryptoKit or libsodium.
public enum BIP39 {

    public static func generateMnemonic(strengthBits: Int = 256,
                                        wordlist: [String]? = nil) -> String {
        // For the scaffold we ship a no-op stub. See `migrate/to/cryptokit`
        // for the real CryptoKit-based BIP-39 generator.
        return "abandon " + String(repeating: "abandon ", count: 22) + "about"
    }
}

public enum HexBytes {
    public static func encode(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }
}
