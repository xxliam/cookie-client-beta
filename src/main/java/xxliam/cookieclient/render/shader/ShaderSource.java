package xxliam.cookieclient.render.shader;

import java.util.HashMap;
import java.util.Map;

/**
 * Shader 源码（GLSL 150）。仅搬运 ClickGUI 圆角所需部分。
 * <p>
 * {@code rounded_rect.fsh} 用 SDF（signed distance field）+ smoothstep 做抗锯齿圆角，
 * 与 OpenZen 的弧度、平滑度完全一致。
 */
public enum ShaderSource {

    VERTEX_COLOR("vertex_color.vsh",
            "#version 150\n" +
            "\n" +
            "in vec3 Position;\n" +
            "in vec2 UV0;\n" +
            "in vec4 Color;\n" +
            "\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "\n" +
            "out vec2 TexCoord;\n" +
            "out vec4 FragColor;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n" +
            "    TexCoord = UV0;\n" +
            "    FragColor = Color;\n" +
            "}\n"),

    ROUNDED_RECT("rounded_rect.fsh",
            "#version 150\n" +
            "\n" +
            "in vec2 TexCoord;\n" +
            "in vec4 FragColor;\n" +
            "\n" +
            "uniform vec2 Size;\n" +
            "uniform float Radius;\n" +
            "uniform float Smoothness;\n" +
            "\n" +
            "out vec4 OutColor;\n" +
            "\n" +
            "float roundSDF(vec2 p, vec2 b, float r) {\n" +
            "    return length(max(abs(p) - b, 0.0)) - r;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 halfSize = Size * .5;\n" +
            "    float smoothedAlpha = (1.0 - smoothstep(1.0 - Smoothness, 1.0, roundSDF(halfSize - (TexCoord * Size), halfSize - Radius - Smoothness * 0.5f, Radius))) * FragColor.a;\n" +
            "\n" +
            "    if (smoothedAlpha == 0.0) discard;\n" +
            "\n" +
            "    OutColor = vec4(FragColor.rgb, smoothedAlpha);\n" +
            "}\n"),

    ROUNDED_TEXTURE("rounded_texture.fsh",
            "#version 150\n" +
            "\n" +
            "in vec2 TexCoord;\n" +
            "in vec4 FragColor;\n" +
            "\n" +
            "uniform vec2 Size;\n" +
            "uniform float Radius;\n" +
            "uniform float Smoothness;\n" +
            "uniform sampler2D ScreenTex;\n" +
            "uniform vec4 Region;\n" +
            "uniform float Lod;\n" +
            "\n" +
            "out vec4 OutColor;\n" +
            "\n" +
            "float roundSDF(vec2 p, vec2 b, float r) {\n" +
            "    return length(max(abs(p) - b, 0.0)) - r;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 halfSize = Size * .5;\n" +
            "    float smoothedAlpha = (1.0 - smoothstep(1.0 - Smoothness, 1.0, roundSDF(halfSize - (TexCoord * Size), halfSize - Radius - Smoothness * 0.5f, Radius))) * FragColor.a;\n" +
            "\n" +
            "    if (smoothedAlpha <= 0.0) discard;\n" +
            "\n" +
            "    vec2 uv = mix(Region.xy, Region.zw, TexCoord);\n" +
            "    vec4 texel = textureLod(ScreenTex, uv, Lod);\n" +
            "    OutColor = vec4(texel.rgb * FragColor.rgb, texel.a * smoothedAlpha);\n" +
            "}\n"),

    RING("ring.fsh",
            "#version 150\n" +
            "\n" +
            "in vec2 TexCoord;\n" +
            "in vec4 FragColor;\n" +
            "\n" +
            "uniform vec2 Size;\n" +
            "uniform float Radius;\n" +
            "uniform float Width;\n" +
            "uniform float Feather;\n" +
            "\n" +
            "out vec4 OutColor;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 center = Size * .5;\n" +
            "    float dist = length(TexCoord * Size - center) - Radius;\n" +
            "    float ringAlpha = 1.0 - smoothstep(-Feather, Feather, abs(dist) - Width * .5);\n" +
            "    float smoothedAlpha = ringAlpha * FragColor.a;\n" +
            "    if (smoothedAlpha == 0.0) discard;\n" +
            "    OutColor = vec4(FragColor.rgb, smoothedAlpha);\n" +
            "}\n");

    private final String fileName;
    private final String source;
    private static final Map<String, ShaderSource> BY_FILENAME = new HashMap<>();

    ShaderSource(String fileName, String source) {
        this.fileName = fileName;
        this.source = source;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSource() {
        return source;
    }

    public static ShaderSource getByFileName(String fileName) {
        return BY_FILENAME.get(fileName);
    }

    static {
        for (ShaderSource shader : values()) {
            BY_FILENAME.put(shader.getFileName(), shader);
        }
    }
}
