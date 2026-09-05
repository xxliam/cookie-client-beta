package xxliam.cookieclient.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;

/**
 * GL shader 程序封装：编译 vertex/fragment、链接、管理 ModelView/ProjMat 与其它 uniform。
 * <p>
 * 搬运自 OpenZen 的 {@code shit.zen.render.shader.ShaderProgram}（去 lombok）。
 */
public class ShaderProgram {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProgram.class);

    private final int programId;
    private final boolean valid;
    private final Map<String, Integer> uniformCache = new HashMap<>();
    private final Matrix4Uniform modelViewUniform;
    private final Matrix4Uniform projectionUniform;
    private Matrix4f cachedModelView;
    private Matrix4f cachedProjection;
    private static int prevProgram;

    public ShaderProgram(String fragmentName, String vertexName, Supplier<Map<Integer, String>> attributesSupplier) {
        this(fragmentName, vertexName, attributesSupplier, "ModelViewMat", "ProjMat");
    }

    public ShaderProgram(String fragmentName, String vertexName, Supplier<Map<Integer, String>> attributesSupplier,
                         String modelViewName, String projName) {
        this.programId = GL20.glCreateProgram();
        ShaderSource fragSrc = ShaderSource.getByFileName(fragmentName + ".fsh");
        ShaderSource vertSrc = ShaderSource.getByFileName(vertexName + ".vsh");
        int fragmentShader = fragSrc != null ? compileShader(fragSrc.getSource(), 35632) : 0;
        int vertexShader = vertSrc != null ? compileShader(vertSrc.getSource(), 35633) : 0;
        if (fragmentShader == 0 || vertexShader == 0) {
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            LOGGER.error("Shader '{}' disabled: compilation failed; the effect will be skipped.", fragmentName);
            this.modelViewUniform = null;
            this.projectionUniform = null;
            this.valid = false;
            return;
        }
        GL20.glAttachShader(this.programId, fragmentShader);
        GL20.glAttachShader(this.programId, vertexShader);
        for (Map.Entry<Integer, String> entry : attributesSupplier.get().entrySet()) {
            GL20.glEnableVertexAttribArray(entry.getKey());
            GL20.glBindAttribLocation(this.programId, entry.getKey(), entry.getValue());
        }
        GL20.glLinkProgram(this.programId);
        if (GL20.glGetProgrami(this.programId, 35714) == 0) {
            LOGGER.error(GL20.glGetProgramInfoLog(this.programId, Short.MAX_VALUE));
            LOGGER.error("Shader '{}' disabled: program link failed; the effect will be skipped.", fragmentName);
            GL20.glDeleteShader(fragmentShader);
            GL20.glDeleteShader(vertexShader);
            this.modelViewUniform = null;
            this.projectionUniform = null;
            this.valid = false;
            return;
        }
        GL20.glDeleteShader(fragmentShader);
        GL20.glDeleteShader(vertexShader);
        this.modelViewUniform = new Matrix4Uniform(modelViewName).bindToProgram(this.programId);
        this.projectionUniform = new Matrix4Uniform(projName).bindToProgram(this.programId);
        this.valid = true;
    }

    public void use() {
        if (!this.valid) {
            return;
        }
        prevProgram = GL20.glGetInteger(35725);
        GL20.glUseProgram(this.programId);
        setModelView(RenderSystem.getModelViewMatrix());
        setProjection(RenderSystem.getProjectionMatrix());
    }

    public void setModelView(Matrix4f modelView) {
        if (this.cachedModelView != modelView) {
            this.modelViewUniform.upload(modelView);
            this.cachedModelView = modelView;
        }
    }

    public void setProjection(Matrix4f projection) {
        if (this.cachedProjection != projection) {
            this.projectionUniform.upload(projection);
            this.cachedProjection = projection;
        }
    }

    public void stopUsing() {
        GL20.glUseProgram(prevProgram);
    }

    public int getUniformLocation(String uniformName) {
        return uniformCache.computeIfAbsent(uniformName, n -> GL20.glGetUniformLocation(this.programId, n));
    }

    public boolean isValid() {
        return valid;
    }

    private static int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        Matcher matcher = ShaderFormats.IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            boolean isPlainImport = matcher.group(2) == null;
            if (!isPlainImport) {
                continue;
            }
            String importName = matcher.group(3);
            ShaderSource importSource = ShaderSource.getByFileName(importName);
            if (importSource == null) {
                continue;
            }
            source = source.replace(importName, importSource.getSource());
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, 35713) == 0) {
            LOGGER.error("Failed to compile shader (type {}):\n{}", type, GL20.glGetShaderInfoLog(shader, Short.MAX_VALUE));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
