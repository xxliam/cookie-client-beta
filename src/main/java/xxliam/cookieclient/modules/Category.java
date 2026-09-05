package xxliam.cookieclient.modules;

/**
 * 模块分类，顺序与 OpenZen 的 Category 对齐。
 */
public enum Category {

    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    EXPLOIT("Exploit"),
    WORLD("World"),
    MISC("Misc");

    public final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Category fromString(String name) {
        for (Category category : values()) {
            if (category.displayName.equalsIgnoreCase(name)) {
                return category;
            }
        }
        return COMBAT;
    }
}
