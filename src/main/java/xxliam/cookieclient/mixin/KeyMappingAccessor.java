package xxliam.cookieclient.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * KeyMapping 点击计数访问器：AutoClicker 用它累加 attack/use 的 clickCount，
 * 让 vanilla handleKeybinds 的 {@code while(key.consumeClick()) startAttack/startUseItem}
 * 像真实鼠标点击一样消费并执行（与 MouseHandler 物理点击走完全相同的路径）。
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int cookieClient$getClickCount();

    @Accessor("clickCount")
    void cookieClient$setClickCount(int clickCount);
}
