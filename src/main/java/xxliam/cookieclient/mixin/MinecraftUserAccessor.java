package xxliam.cookieclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Minecraft.user 访问器：1.20.1 中该字段为 private final，
 * 需 @Mutable 让 Mixin 在应用时剥离 final 才能写入（ALT 管理换号用）。
 */
@Mixin(Minecraft.class)
public interface MinecraftUserAccessor {

    @Mutable
    @Accessor("user")
    void cookieClient$setUser(User user);
}
