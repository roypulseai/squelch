// swift-tools-version:5.7
//
// Squelch P2P iOS scaffold (spec section 4). Open this Package.swift
// in Xcode 14+ and add to a SwiftUI app to build. Cannot be compiled on
// Windows.

import PackageDescription

let package = Package(
    name: "SquelchP2P",
    platforms: [
        .iOS(.v14)
    ],
    products: [
        .library(
            name: "SquelchP2P",
            targets: ["SquelchP2P"]
        )
    ],
    targets: [
        .target(
            name: "SquelchP2P",
            path: "SquelchP2P/Sources"
        ),
        .testTarget(
            name: "SquelchP2PTests",
            dependencies: ["SquelchP2P"],
            path: "SquelchP2P/Tests"
        )
    ]
)
