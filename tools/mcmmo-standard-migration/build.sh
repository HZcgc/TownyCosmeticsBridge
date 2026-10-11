#!/bin/sh
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
task_java=${MIGRATION_JAVA_HOME:-}
if [ -n "$task_java" ]; then
    task_javac="$task_java/bin/javac"
    task_jar="$task_java/bin/jar"
    task_runtime="$task_java/bin/java"
else
    task_javac=javac
    task_jar=jar
    task_runtime=java
fi
mkdir -p "$task_root/build/classes" "$task_root/build/test-classes"
"$task_javac" --release 17 -d "$task_root/build/classes" "$task_root/src/net/townyreborn/mcmmo/Main.java"
cp "$task_root/resources/README-DE.txt" "$task_root/build/classes/README-DE.txt"
"$task_jar" --create --file "$task_root/build/TownyReborn-mcMMO-Standard-Converter.jar" --main-class net.townyreborn.mcmmo.Main -C "$task_root/build/classes" .
"$task_javac" --release 17 -cp "$task_root/build/classes" -d "$task_root/build/test-classes" "$task_root/test/net/townyreborn/mcmmo/MainTest.java"
"$task_runtime" -cp "$task_root/build/classes:$task_root/build/test-classes" net.townyreborn.mcmmo.MainTest
