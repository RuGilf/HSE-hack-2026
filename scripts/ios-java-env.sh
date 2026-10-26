#!/bin/sh
# Xcode launched from Finder does not inherit the terminal's JAVA_HOME.
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  elif [ -x /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  else
    JAVA_HOME=$(/usr/libexec/java_home --failfast -v 21) || {
      echo 'Install JDK 21 or set JAVA_HOME before building Lifety.' >&2
      exit 1
    }
  fi
  export JAVA_HOME
fi
