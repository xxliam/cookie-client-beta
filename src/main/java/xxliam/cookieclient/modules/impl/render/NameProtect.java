package xxliam.cookieclient.modules.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import xxliam.cookieclient.modules.Category;
import xxliam.cookieclient.modules.Module;
import xxliam.cookieclient.settings.impl.ModeSetting;

import java.util.ArrayList;
import java.util.Random;

/**
 * NameProtect：匿名，把聊天里的自己名字替换成假名（Fixed 固定 / Random 随机）。
 * <p>
 * 聊天替换由 {@code ChatComponentMixin} 调用 {@link #replacePlayerName(String)} 实现。
 */
public class NameProtect extends Module {

    public static NameProtect INSTANCE;
    private final ModeSetting modeSetting = new ModeSetting("Mode", "Fixed", "Random").withDefault("Fixed");

    private String cachedRandomName;
    private final Random random = new Random();

    public NameProtect() {
        super("NameProtect", Category.RENDER);
        INSTANCE = this;
        addSetting(modeSetting);
    }

    /** 把字符串中出现的自己名字替换成假名。 */
    public static String replacePlayerName(String string) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || string == null) {
            return string;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return string;
        }
        String ownName = mc.player.getName().getString();
        String protectedName = INSTANCE.generateRandomName();
        if (protectedName != null && !protectedName.equals(ownName) && string.contains(ownName)) {
            return string.replace(ownName, protectedName);
        }
        return string;
    }

    /** 返回保护后的显示名。 */
    public static String getProtectedName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "Player";
        }
        if (INSTANCE == null || !INSTANCE.isEnabled()) {
            return mc.player.getName().getString();
        }
        String ownName = mc.player.getName().getString();
        String protectedName = INSTANCE.generateRandomName();
        return protectedName != null && !protectedName.equals(ownName) ? protectedName : ownName;
    }

    /** 从在线玩家列表里随机取一个他人的名字作为假名（Fixed 模式持续使用同一名字）。 */
    private String generateRandomName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return null;
        }
        ArrayList<PlayerInfo> players = new ArrayList<>(mc.getConnection().getOnlinePlayers());
        ArrayList<String> names = new ArrayList<>();
        String ownName = mc.player.getName().getString();
        for (PlayerInfo info : players) {
            String n = info.getProfile().getName();
            if (!n.equals(ownName)) {
                names.add(n);
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        if (cachedRandomName == null || !names.contains(cachedRandomName)) {
            cachedRandomName = names.get(random.nextInt(names.size()));
        }
        return cachedRandomName;
    }
}
