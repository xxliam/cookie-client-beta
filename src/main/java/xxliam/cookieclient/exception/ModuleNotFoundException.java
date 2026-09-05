package xxliam.cookieclient.exception;

/**
 * 模块不存在异常。
 */
public class ModuleNotFoundException extends RuntimeException {

    public ModuleNotFoundException(String moduleName) {
        super("Module not found: " + moduleName);
    }
}
