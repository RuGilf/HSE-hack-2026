#!/bin/sh
set -eu
PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$PROJECT_ROOT/scripts/ios-java-env.sh"
exec xcodebuild -project "$PROJECT_ROOT/iosApp/Lifety.xcodeproj" \
  -scheme Lifety -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$PROJECT_ROOT/build/ios" build "$@"
