package xxliam.cookieclient.render.shader;

import java.nio.FloatBuffer;

/**
 * FloatBuffer 辅助。
 */
public final class BufferUtil {

    private BufferUtil() {
    }

    public static void fill(FloatBuffer buffer, float value) {
        buffer.clear();
        for (int i = 0; i < buffer.capacity(); ++i) {
            buffer.put(i, value);
        }
        buffer.clear();
    }

    public static FloatBuffer storeMatrix(FloatBuffer buffer, org.joml.Matrix4f matrix) {
        return matrix.get(buffer);
    }
}
