package xxliam.cookieclient.utils.misc;

import java.io.InputStream;

/**
 * 统一资源打开器：从 classpath 读取资源。
 */
public final class Assets {

    private Assets() {
    }

    public static InputStream open(String classpath) {
        return Assets.class.getResourceAsStream(classpath);
    }
}
