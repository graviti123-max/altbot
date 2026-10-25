package pl.altbot.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Pozwala podmienic sesje (nick) klienta, zeby wejsc na serwer jako konto non-premium. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("user")
    User altbot$getUser();

    @Mutable
    @Accessor("user")
    void altbot$setUser(User user);
}
