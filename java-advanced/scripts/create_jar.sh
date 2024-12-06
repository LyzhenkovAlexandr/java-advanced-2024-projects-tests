#!/bin/bash

if [[ "$OSTYPE" == "cygwin" || "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    separator=";"
else
    separator=":"
fi

bin_path="../bin"
class_path_1="../../java-advanced-2024/artifacts/info.kgeorgiy.java.advanced.implementor.jar"
class_path_2="../java-solutions/"
class_path="$class_path_1${separator}$class_path_2"
implementor_path="../java-solutions/info/kgeorgiy/ja/lyzhenkov/implementor/Implementor.java"
name_jar="implementor.jar"
manifest_path="./MANIFEST.MF"
path_for_save_jar="."

javac -d ${bin_path} -cp ${class_path} ${implementor_path}
jar -cfm ${name_jar} ${manifest_path} -C ${bin_path} ${path_for_save_jar}

rm -r ${bin_path}
