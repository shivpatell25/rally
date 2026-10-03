import SwiftUI

/// Place stable upcoming children at their real layout positions. Post-layout
/// offsets make tvOS reason about stale horizontal focus frames during expansion.
struct RallyUpcomingLayout: Layout {
  var progress: CGFloat
  var animatableData: CGFloat {
    get { progress }
    set { progress = newValue }
  }
  func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
    let p = min(1, max(0, progress))
    return CGSize(width: RallyDesign.pt(840), height: RallyDesign.pt(64 + (CGFloat(subviews.count) * 34 - 64) * p))
  }
  func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
    let p = min(1, max(0, progress))
    let width = RallyDesign.pt(204 + 636 * p), height = RallyDesign.pt(64 - 30 * p)
    for (index, view) in subviews.enumerated() {
      view.place(at: CGPoint(x: bounds.minX + RallyDesign.pt(CGFloat(index) * 212 * (1 - p)),
                             y: bounds.minY + RallyDesign.pt(CGFloat(index) * 34 * p)),
                 anchor: .topLeading, proposal: ProposedViewSize(width: width, height: height))
    }
  }
}
