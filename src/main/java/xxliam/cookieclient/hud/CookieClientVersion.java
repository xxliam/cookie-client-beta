package xxliam.cookieclient.hud;

/**
 * Cookie 水印品牌文案（替代 OpenZen 的 ZEN/beta/b1）。
 * <p>
 * VERSION 与 {@code fabric.mod.json} 的 {@code version} 保持一致（beta1.0，
 * 沿用 zen 的 cleanVersion 语义：去掉可能存在的 v 前缀）。
 */
public final class CookieClientVersion {

    /** 品牌短名（对应 zen 原版 logo 旁的短品牌；加载界面 logo 替换也用它）。 */
    public static final String BRAND = "Cookie";
    /** 版本短标签。 */
    static final String VERSION = "beta3";

    private CookieClientVersion() {
    }
}
