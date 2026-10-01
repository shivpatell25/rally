import SwiftUI

struct RallyComparisonBar: View {
  let value: String
  let other: String
  let color: Color
  private func magnitude(_ text: String) -> Double {
    let parts = text.replacingOccurrences(of: "%", with: "").split(separator: "/")
      .compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
    if parts.count == 2, parts[1] > 0 { return parts[0] / parts[1] }
    return max(0, parts.first ?? 0)
  }
  var body: some View {
    GeometryReader { geometry in
      let a = magnitude(value)
      let b = magnitude(other)
      ZStack(alignment: .leading) {
        Capsule().fill(.white.opacity(0.08))
        Capsule().fill(color).frame(width: geometry.size.width * (a + b > 0 ? a / (a + b) : 0))
      }
    }.frame(width: RallyDesign.pt(30), height: RallyDesign.pt(3))
      .accessibilityHidden(true)
  }
}
