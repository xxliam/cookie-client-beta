package xxliam.cookieclient.notification;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import xxliam.cookieclient.CookieClient;

/**
 * 通知提示音（模块开关 on/off）。
 * <p>
 * 音频资源位于 {@code assets/cookie-client/sounds/notify_on.ogg} / {@code notify_off.ogg}，
 * 事件名在 {@code assets/cookie-client/sounds.json} 中声明（key = notify.on / notify.off）；
 * 播放前需在 {@link CookieClient#onInitialize()} 调用 {@link #register()}，
 * 并在 Notifications 模块的 Sound 开关开启时经 {@link #play(boolean)} 触发。
 * <p>
 * 播放走 {@link SoundManager}（声音引擎已加载时才会出声，未加载时静默忽略），
 * 分类为 UI（Master 音量），与 vanilla 按钮点击同路径。
 */
public final class NotificationSounds {

    public static final ResourceLocation ON_ID = new ResourceLocation(CookieClient.MOD_ID, "notify.on");
    public static final ResourceLocation OFF_ID = new ResourceLocation(CookieClient.MOD_ID, "notify.off");

    public static final SoundEvent NOTIFY_ON = SoundEvent.createVariableRangeEvent(ON_ID);
    public static final SoundEvent NOTIFY_OFF = SoundEvent.createVariableRangeEvent(OFF_ID);

    private NotificationSounds() {
    }

    /** 将两个声音事件注册进内置声音注册表（Fabric 1.20.1 惯例做法，需在 onInitialize 调用一次）。 */
    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, ON_ID, NOTIFY_ON);
        Registry.register(BuiltInRegistries.SOUND_EVENT, OFF_ID, NOTIFY_OFF);
    }

    /** 播放开关提示音：模块开启 = on，关闭 = off。声音引擎未就绪时自动忽略。 */
    public static void play(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        SoundManager soundManager = mc.getSoundManager();
        if (soundManager == null) {
            return;
        }
        SoundEvent event = enabled ? NOTIFY_ON : NOTIFY_OFF;
        soundManager.play(SimpleSoundInstance.forUI(event, 1.0f));
    }
}
