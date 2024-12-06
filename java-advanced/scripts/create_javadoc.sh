#!/bin/bash

if [[ "$OSTYPE" == "cygwin" || "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
    separator=";"
else
    separator=":"
fi

JDK_21="https://docs.oracle.com/en/java/javase/21/docs/api/"
doc_path="../javadoc"
classpath="../../java-advanced-2024/artifacts/info.kgeorgiy.java.advanced.implementor.jar${separator}../java-solutions"

javadoc -d ${doc_path} \
        -link ${JDK_21} \
        -private \
        -author \
        -version \
        -cp ${classpath} \
        ../java-solutions/info/kgeorgiy/ja/lyzhenkov/implementor/Implementor.java \
        ../java-solutions/info/kgeorgiy/ja/lyzhenkov/implementor/ClassSupplier.java \
        ../../java-advanced-2024/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/Impler.java \
        ../../java-advanced-2024/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/JarImpler.java \
        ../../java-advanced-2024/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor/ImplerException.java
