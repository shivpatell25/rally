import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Packages the unchanged official Android artwork into Apple's layered icon format.
// Run from apple/RallyTV: swift package_brand_assets.swift
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let resources = root.appendingPathComponent("RallyTV/Resources")
let catalog = resources.appendingPathComponent("Brand.xcassets")
let brand = catalog.appendingPathComponent("Rally.brandassets")
let info: [String: Any] = ["author": "com.shiv.rally.tv", "version": 1]
func json(_ value: [String: Any], at directory: URL) throws {
  try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
  try JSONSerialization.data(withJSONObject: value, options: [.prettyPrinted, .sortedKeys])
    .write(to: directory.appendingPathComponent("Contents.json"))
}
func load(_ name: String) -> CGImage {
  let source = CGImageSourceCreateWithURL(resources.appendingPathComponent(name) as CFURL, nil)!
  return CGImageSourceCreateImageAtIndex(source, 0, nil)!
}
let wordmark = load("rally_wordmark_color_ui.png")
let flare = load("rally_tv_background_v8.png")
func render(width: Int, height: Int, foreground: Bool, topShelf: Bool = false) -> CGImage {
  let w = CGFloat(width)
  let h = CGFloat(height)
  let context = CGContext(
    data: nil, width: width, height: height, bitsPerComponent: 8,
    bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
  context.interpolationQuality = .high
  if foreground {
    let scale = min(
      w * 0.68 / CGFloat(wordmark.width), h * 0.44 / CGFloat(wordmark.height))
    let mw = CGFloat(wordmark.width) * scale
    let mh = CGFloat(wordmark.height) * scale
    context.draw(wordmark, in: CGRect(x: (w - mw) / 2, y: (h - mh) / 2, width: mw, height: mh))
  } else {
    context.setFillColor(CGColor(red: 5 / 255, green: 5 / 255, blue: 7 / 255, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: w, height: h))
    context.setAlpha(0.42)
    let scale = max(w / CGFloat(flare.width), h / CGFloat(flare.height))
    context.draw(
      flare,
      in: CGRect(
        x: 0, y: h - CGFloat(flare.height) * scale,
        width: CGFloat(flare.width) * scale, height: CGFloat(flare.height) * scale))
    context.setAlpha(1)
    if topShelf {
      let logo = load("rally_wordmark_color_ui.png")
      let factor = min(w * 0.25 / CGFloat(logo.width), h * 0.24 / CGFloat(logo.height))
      let lw = CGFloat(logo.width) * factor
      let lh = CGFloat(logo.height) * factor
      context.draw(logo, in: CGRect(x: (w - lw) / 2, y: (h - lh) / 2, width: lw, height: lh))
    }
  }
  return context.makeImage()!
}
func png(_ image: CGImage, to url: URL) {
  let destination = CGImageDestinationCreateWithURL(
    url as CFURL, UTType.png.identifier as CFString, 1, nil)!
  CGImageDestinationAddImage(destination, image, nil)
  precondition(CGImageDestinationFinalize(destination))
}
try json(["info": info], at: catalog)
var assets: [[String: String]] = []
for (name, width, height, scales) in [
  ("App Icon", 400, 240, [1, 2]), ("App Store Icon", 1280, 768, [1]),
] {
  let filename = name + ".imagestack"
  let stack = brand.appendingPathComponent(filename)
  try json(
    [
      "info": info,
      "layers": [["filename": "Front.imagestacklayer"], ["filename": "Back.imagestacklayer"]],
    ], at: stack)
  for (layer, foreground) in [("Front", true), ("Back", false)] {
    let images = stack.appendingPathComponent(layer + ".imagestacklayer/Content.imageset")
    let entries = scales.map { scale in
      ["idiom": "tv", "scale": "\(scale)x", "filename": "image-\(scale)x.png"]
    }
    try json(["info": info, "images": entries], at: images)
    for scale in scales {
      png(
        render(width: width * scale, height: height * scale, foreground: foreground),
        to: images.appendingPathComponent("image-\(scale)x.png"))
    }
  }
  assets.append([
    "idiom": "tv", "size": "\(width)x\(height)", "filename": filename, "role": "primary-app-icon",
  ])
}
for (name, width, role) in [
  ("Top Shelf", 1920, "top-shelf-image"), ("Top Shelf Wide", 2320, "top-shelf-image-wide"),
] {
  let filename = name + ".imageset"
  let images = brand.appendingPathComponent(filename)
  try json(
    [
      "info": info,
      "images": [1, 2].map { ["idiom": "tv", "scale": "\($0)x", "filename": "image-\($0)x.png"] },
    ], at: images)
  for scale in [1, 2] {
    png(
      render(width: width * scale, height: 720 * scale, foreground: false, topShelf: true),
      to: images.appendingPathComponent("image-\(scale)x.png"))
  }
  assets.append(["idiom": "tv", "size": "\(width)x720", "filename": filename, "role": role])
}
try json(["info": info, "assets": assets], at: brand)
