#!/usr/bin/env swift
// Rebuild bundled geometric app marks from LifetyPalette.kt, without image editing.
// Run from the repository root: swift scripts/generate-themed-icons.swift
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let source = try String(contentsOf: root.appendingPathComponent("shared/src/commonMain/kotlin/ru/lifety/core/appearance/LifetyPalette.kt"), encoding: .utf8)
let entry = try NSRegularExpression(pattern: #"(?m)^    (\w+)\("([^"]+)",.*ThemePalette\((.*)\)\)[,;]"#)
let hex = try NSRegularExpression(pattern: #"Color\(0xFF([0-9A-F]{6})\)"#)
let ns = source as NSString
let original = try String(contentsOf: root.appendingPathComponent("androidApp/src/main/res/drawable/ic_lifety.xml"), encoding: .utf8)
func color(_ value: String) -> CGColor {
    let rgb = UInt32(value, radix: 16)!
    return CGColor(red: CGFloat((rgb >> 16) & 255) / 255, green: CGFloat((rgb >> 8) & 255) / 255, blue: CGFloat(rgb & 255) / 255, alpha: 1)
}
for match in entry.matches(in: source, range: NSRange(location: 0, length: ns.length)) {
    let name = ns.substring(with: match.range(at: 1))
    let id = ns.substring(with: match.range(at: 2))
    if name == "Lifety" { continue } // Preserve the existing primary icon.
    let palette = ns.substring(with: match.range(at: 3)) as NSString
    let values = hex.matches(in: palette as String, range: NSRange(location: 0, length: palette.length)).map { palette.substring(with: $0.range(at: 1)) }
    precondition(values.count == 8)
    let background = values[0], accent = values[3]
    let directory = root.appendingPathComponent("iosApp/Lifety/Assets.xcassets/AppIcon\(name).appiconset")
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let context = CGContext(data: nil, width: 1024, height: 1024, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    context.setFillColor(color(background)); context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))
    context.scaleBy(x: 1024 / 108, y: 1024 / 108)
    context.translateBy(x: 0, y: 108); context.scaleBy(x: 1, y: -1)
    context.setStrokeColor(color(accent)); context.setLineWidth(4); context.setLineCap(.round)
    context.strokeEllipse(in: CGRect(x: 24, y: 24, width: 60, height: 60))
    context.move(to: CGPoint(x: 42, y: 57))
    context.addCurve(to: CGPoint(x: 66, y: 57), control1: CGPoint(x: 46, y: 69), control2: CGPoint(x: 62, y: 69))
    context.strokePath()
    let output = CGImageDestinationCreateWithURL(directory.appendingPathComponent("AppIcon.png") as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(output, context.makeImage()!, nil)
    precondition(CGImageDestinationFinalize(output))
    try """
    {"images":[{"filename":"AppIcon.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"author":"xcode","version":1}}
    """.write(to: directory.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
    let vector = original.replacingOccurrences(of: "#101512", with: "#" + background).replacingOccurrences(of: "#35EF70", with: "#" + accent)
    try vector.write(to: root.appendingPathComponent("androidApp/src/main/res/drawable/ic_lifety_\(id.replacingOccurrences(of: "-", with: "_")).xml"), atomically: true, encoding: .utf8)
    print("Built \(name)")
}
