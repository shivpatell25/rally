import SwiftUI
import UIKit

/// Bundle artwork loader. `Image(name:)` misses loose JPEG resources, so art
/// resolves through an explicit bundle path with a shared decode cache.
public enum BundleArt {
  private static let cache = NSCache<NSString, UIImage>()

  public static func image(_ name: String) -> Image {
    if let uiImage = uiImage(name) {
      return Image(uiImage: uiImage)
    }
    return Image(systemName: "photo")
  }

  public static func uiImage(_ name: String) -> UIImage? {
    if let cached = cache.object(forKey: name as NSString) { return cached }
    let file: String
    let ext: String
    if let dot = name.lastIndex(of: "."), !name.hasSuffix("/") {
      file = String(name[..<dot])
      ext = String(name[name.index(after: dot)...])
    } else {
      file = name
      ext = "jpg"
    }
    guard let path = Bundle.main.path(forResource: file, ofType: ext),
      let loaded = UIImage(contentsOfFile: path)
    else { return nil }
    cache.setObject(loaded, forKey: name as NSString)
    return loaded
  }
}
