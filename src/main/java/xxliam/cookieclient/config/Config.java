package xxliam.cookieclient.config;

import java.nio.file.Path;

/**
 * 配置基类。所有配置（模块状态 / 设置值 / 按键绑定）统一存放在
 * 游戏运行目录下的 {@code cookie-client/} 文件夹。
 * <p>
 * {@link #DIRECTORY} 公开，便于调试或后续命令直接定位该目录。
 */
public abstract class Config {

    public static final Path DIRECTORY = Path.of("cookie-client");

    private final String fileName;

    protected Config(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    protected Path getPath() {
        return DIRECTORY.resolve(fileName);
    }
}
