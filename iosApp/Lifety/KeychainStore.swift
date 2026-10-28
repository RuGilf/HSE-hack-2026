import Foundation
import Security
import CommonCrypto
import LifetyShared

final class KeychainStore: IosCredentialStorage {
    private let service: String
    init(service: String = "ru.lifety.ios.agent") { self.service = service }
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: "provider"]
    }
    func read() -> IosSecretResult {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return IosSecretResult(value: nil, error: nil) }
        guard status == errSecSuccess, let data = result as? Data,
              let text = String(data: data, encoding: .utf8) else {
            return IosSecretResult(value: nil, error: "Keychain read failed")
        }
        return IosSecretResult(value: text, error: nil)
    }
    func write(value: String) -> Bool {
        let attributes: [String: Any] = [kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let updated = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updated == errSecSuccess { return true }
        guard updated == errSecItemNotFound else { return false }
        return SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) == errSecSuccess
    }
}

final class SectionLockStorage: IosSectionLockStorage {
    private let storage = KeychainStore(service: "ru.lifety.ios.section-lock")
    func read() -> IosSecretResult { storage.read() }
    func write(value: String) -> Bool { storage.write(value: value) }
    func createSalt() -> IosSecretResult {
        var bytes = [UInt8](repeating: 0, count: 16)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
            return IosSecretResult(value: nil, error: "Secure randomness unavailable")
        }
        return IosSecretResult(value: Data(bytes).base64EncodedString(), error: nil)
    }
    func derivePin(pin: String, salt: String) -> IosSecretResult {
        guard let saltData = Data(base64Encoded: salt), saltData.count == 16 else {
            return IosSecretResult(value: nil, error: "Invalid salt")
        }
        var derived = [UInt8](repeating: 0, count: 32)
        let password = Array(pin.utf8)
        let status = password.withUnsafeBytes { p in saltData.withUnsafeBytes { s in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), p.baseAddress?.assumingMemoryBound(to: Int8.self), password.count,
                s.baseAddress?.assumingMemoryBound(to: UInt8.self), saltData.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA1),
                1_300_000, &derived, 32)
        } }
        guard status == kCCSuccess else { return IosSecretResult(value: nil, error: "Key derivation failed") }
        return IosSecretResult(value: Data(derived).base64EncodedString(), error: nil)
    }
}
