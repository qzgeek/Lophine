// libdeflate gzip 压缩 JNI 绑定（Lophine 玩家数据保存加速）
//
// 替代 Java GZIPOutputStream（zlib/Deflater），libdeflate 快 3-5 倍。
// libdeflate_gzip_compress 生成标准 gzip 格式（RFC 1952），
// 与 GZIPInputStream / NbtIo.readCompressed 完全兼容（特性不变）。
//
// 编译：
//   g++ -O3 -fPIC -shared -I$JAVA_HOME/include -I$JAVA_HOME/include/linux \
//       -o liblophine_gzip.so gzip_jni.cpp -ldeflate
#include <jni.h>
#include <cstdint>
#include <cstddef>
#include <cstdlib>

struct libdeflate_compressor;

// 手动声明 libdeflate 符号（系统 libdeflate.so.0，无需头文件）
extern "C" {
    struct libdeflate_compressor* libdeflate_alloc_compressor(int compression_level);
    void libdeflate_free_compressor(struct libdeflate_compressor* c);
    size_t libdeflate_gzip_compress_bound(struct libdeflate_compressor* c, size_t in_nbytes);
    size_t libdeflate_gzip_compress(struct libdeflate_compressor* c,
                                    const void* in, size_t in_nbytes,
                                    void* out, size_t out_nbytes_avail);
}

// 每线程一个压缩器（libdeflate_compressor 非线程安全；Lophine 写盘是单线程，
// 但为稳妥用 thread_local，避免任何并发复用）
static thread_local libdeflate_compressor* g_compressor = nullptr;

static libdeflate_compressor* get_compressor() {
    if (g_compressor == nullptr) {
        g_compressor = libdeflate_alloc_compressor(6); // 与 Deflater 默认级别对齐
    }
    return g_compressor;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_fun_bm_lophine_utils_LophineGzip_compress(JNIEnv* env, jclass, jbyteArray input) {
    if (input == nullptr) return nullptr;
    libdeflate_compressor* c = get_compressor();
    if (c == nullptr) return nullptr; // 分配失败

    jsize len = env->GetArrayLength(input);
    jbyte* in = env->GetByteArrayElements(input, nullptr);
    if (in == nullptr) return nullptr;

    // bound = 压缩后最大可能大小（含 gzip 头 + 数据 + CRC32 + ISIZE）
    size_t bound = libdeflate_gzip_compress_bound(c, (size_t)len);
    uint8_t* out = (uint8_t*)malloc(bound > 0 ? bound : 1);
    size_t actual = 0;
    if (out != nullptr) {
        actual = libdeflate_gzip_compress(c, in, (size_t)len, out, bound);
    }
    env->ReleaseByteArrayElements(input, in, JNI_ABORT);

    if (out == nullptr) return nullptr;
    jbyteArray result = env->NewByteArray((jsize)actual);
    if (result != nullptr && actual > 0) {
        env->SetByteArrayRegion(result, 0, (jsize)actual, (const jbyte*)out);
    }
    free(out);
    return result;
}
