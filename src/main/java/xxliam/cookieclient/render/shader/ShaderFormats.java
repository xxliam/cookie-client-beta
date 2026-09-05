package xxliam.cookieclient.render.shader;

import io.netty.util.collection.IntObjectHashMap;

import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Shader 顶点属性格式与 import 预处理。
 */
public final class ShaderFormats {

    public static final Supplier<Map<Integer, String>> POSITION_UV_COLOR = () -> {
        IntObjectHashMap<String> map = new IntObjectHashMap<>();
        map.put(0, "Position");
        map.put(1, "UV0");
        map.put(2, "Color");
        return map;
    };

    public static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*import(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(?:\"(.*)\"|<(.*)>))");

    private ShaderFormats() {
    }
}
