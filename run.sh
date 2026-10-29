#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build
javac --add-modules jdk.httpserver -encoding UTF-8 -d build src/trustcheck/TrustCheckServer.java
exec java --add-modules jdk.httpserver -cp build trustcheck.TrustCheckServer "$PWD"
