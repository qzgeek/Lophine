package fun.bm.lophine.utils;

/**
 * libdeflate gzip 压缩（C++ 加速）。
 *
 * 替代 Java GZIPOutputStream，压缩提速 3-5 倍，输出为标准 gzip 格式，
 * 与 {@code GZIPInputStream}/{@code NbtIo.readCompressed} 完全兼容（特性不变）。
 *
 * 若原生库不可用，{@link #isAvailable()} 返回 false，调用方回退到 Java 压缩。
 */
public final class LophineGzip {
    private static final boolean AVAILABLE;

    static {
        boolean ok = false;
        try {
            System.loadLibrary("lophine_gzip");
            ok = true;
        } catch (UnsatisfiedLinkError ignored) {
            // .so 不可用，调用方回退 Java 压缩
        }
        AVAILABLE = ok;
    }

    private LophineGzip() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * 压缩字节为 gzip 格式。返回 null 表示失败（调用方应回退）。
     */
    public static native byte[] compress(byte[] input);
}
