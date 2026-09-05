package xxliam.cookieclient.config;

/**
 * 配置基类。所有配置（模块状态 / 设置值等）统一存放在
 * 游戏运行目录下的 {@code cookie-client/} 文件夹。
 */
public abstract class Config {

    protected static final java.nio.file.Path DIRECTORY = java.nio.file.Path.of("cookie-client");

    protected final String fileName;

    protected Config(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    protected java.nio.file.Path getPath() {
        return DIRECTORY.resolve(fileName);
    }
}
