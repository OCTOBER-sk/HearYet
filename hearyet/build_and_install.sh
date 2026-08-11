#!/bin/bash
cd "$(dirname "$0")"
./gradlew :app:installDebug 2>&1
