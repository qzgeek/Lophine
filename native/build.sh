#!/bin/bash
# 编译 liblophine_gzip.so（libdeflate gzip 压缩 JNI 库）
# 依赖：g++、JDK 头文件、系统 libdeflate.so.0
set -e
cd "$(dirname "$0")"
JH="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
g++ -O3 -fPIC -shared -I"$JH/include" -I"$JH/include/linux" \
    -o liblophine_gzip.so gzip_jni.cpp -l:libdeflate.so.0
echo "已生成 liblophine_gzip.so"
