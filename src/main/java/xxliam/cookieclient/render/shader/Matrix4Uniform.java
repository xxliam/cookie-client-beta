package xxliam.cookieclient.render.shader;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * mat4 uniform，负责把 Matrix4f 上传到 GL。
 */
public class Matrix4Uniform extends Uniform<Matrix4Uniform> {

    private final FloatBuffer dataBuffer = MemoryUtil.memAllocFloat(16);
    private final FloatBuffer stagingBuffer = MemoryUtil.memAllocFloat(16);
    private boolean transpose;

    public Matrix4Uniform(String name) {
        super(name);
    }

    public void upload(Matrix4f matrix) {
        stagingBuffer.clear();
        BufferUtil.storeMatrix(stagingBuffer, matrix);
        uploadRaw(false, stagingBuffer);
    }

    public void uploadRaw(boolean transpose, FloatBuffer buffer) {
        this.transpose = transpose;
        buffer.mark();
        dataBuffer.clear();
        dataBuffer.put(buffer);
        dataBuffer.rewind();
        buffer.reset();
        int location = getLocation();
        if (location >= 0) {
            GL20.glUniformMatrix4fv(location, transpose, dataBuffer);
        }
    }
}
