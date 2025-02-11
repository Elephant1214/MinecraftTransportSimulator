package mcinterface1165.mixin.client;

import minecrafttransportsimulator.systems.LanguageSystem;
import net.minecraft.client.resource.language.LanguageManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin implements SynchronousResourceReloader {
    /**
     * Need this to allow us to populate the language names at the right time.  If we call in the Forge events, this happens too soon
     * and the languages aren't populated yet.
     */
    @Inject(method = "reload", at = @At(value = "TAIL"))
    public void inject_onResourceManagerReload(ResourceManager resourceManager, CallbackInfo ci) {
        if (FMLEnvironment.dist.isClient()) {
            LanguageSystem.populateNames();
        }
    }
}
