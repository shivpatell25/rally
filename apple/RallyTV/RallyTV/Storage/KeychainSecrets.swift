import Foundation
import Security

/// Keychain-backed secrets. Replaces EncryptedSharedPreferences for credentials.
public struct KeychainSecrets: Sendable {
  public let service: String

  public init(service: String = "com.shiv.rally.tv.secrets") {
    self.service = service
  }

  public func string(for key: String) -> String {
    var item: CFTypeRef?
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
      let data = item as? Data,
      let value = String(data: data, encoding: .utf8)
    else { return "" }
    return value
  }

  @discardableResult public func set(_ value: String, for key: String) -> OSStatus {
    guard let data = value.data(using: .utf8) else { return errSecParam }
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]
    let attrs: [String: Any] = [kSecValueData as String: data]
    var status = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
    if status == errSecItemNotFound {
      var add = query
      add[kSecValueData as String] = data
      add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
      status = SecItemAdd(add as CFDictionary, nil)
    }
    if status != errSecSuccess {
      RallyDiagnostics.shared.record("Secure storage", code: "OSStatus \(status)")
    }
    return status
  }

  public func remove(_ key: String) {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: key,
    ]
    SecItemDelete(query as CFDictionary)
  }

  public func clearAll() {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
    ]
    SecItemDelete(query as CFDictionary)
  }
}
