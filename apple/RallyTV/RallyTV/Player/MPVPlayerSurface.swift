import SwiftUI
import UIKit

/// Inline and fullscreen attach the same Metal layer. Moving the layer never reloads media.
struct MPVPlayerSurface: UIViewRepresentable {
  let session: PlaybackSession
  var fullscreen = false
  func makeUIView(context: Context) -> Surface {
    Surface(layer: session.metalLayer, priority: fullscreen ? 1 : 0)
  }
  func updateUIView(_ view: Surface, context: Context) { view.attach() }
  static func dismantleUIView(_ view: Surface, coordinator: ()) { view.detach() }
  final class Surface: UIView {
    private let video: RallyMetalVideoLayer
    private let priority: Int
    private weak var previousSurface: Surface?
    init(layer: RallyMetalVideoLayer, priority: Int = 0) {
      self.priority = priority
      video = layer
      super.init(frame: .zero)
      backgroundColor = .black
      accessibilityIdentifier = "MPVVideoSurface"
      accessibilityLabel = "Video"
      isAccessibilityElement = true
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func attach() {
      guard window != nil else { return }
      if let current = video.superlayer?.delegate as? Surface, current !== self,
        current.priority > priority, current.window != nil
      {
        return
      }
      if video.superlayer !== layer {
        previousSurface = video.superlayer?.delegate as? Surface
        video.removeFromSuperlayer()
        layer.addSublayer(video)
      }
      setNeedsLayout()
    }
    func detach() {
      guard video.superlayer === layer else { return }
      video.removeFromSuperlayer()
      if let previousSurface, previousSurface.window != nil {
        previousSurface.attach()
        previousSurface.layoutIfNeeded()
      }
    }
    override func didMoveToWindow() {
      super.didMoveToWindow()
      if window != nil { attach() }
    }
    override func layoutSubviews() {
      super.layoutSubviews()
      guard video.superlayer === layer, bounds.width > 1, bounds.height > 1 else { return }
      let scale = window?.screen.scale ?? traitCollection.displayScale
      CATransaction.begin()
      CATransaction.setDisableActions(true)
      video.frame = bounds
      video.contentsScale = scale
      video.drawableSize = CGSize(width: bounds.width * scale, height: bounds.height * scale)
      CATransaction.commit()
    }
  }
}
