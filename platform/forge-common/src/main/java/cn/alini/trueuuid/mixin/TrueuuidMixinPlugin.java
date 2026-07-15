package cn.alini.trueuuid.mixin;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Forge discovers production Mixin configs from the jar manifest. Apply the
 * client and dedicated-server login packet adapters only to their matching
 * physical distribution: Minecraft's client and server login packet classes
 * do not expose identical private decode methods.
 */
public final class TrueuuidMixinPlugin implements IMixinConfigPlugin {
    private static final String CLIENT_MIXIN_PREFIX = "cn.alini.trueuuid.mixin.client.";
    private static final String SERVER_MIXIN_PREFIX = "cn.alini.trueuuid.mixin.server.";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(CLIENT_MIXIN_PREFIX)) return FMLEnvironment.dist == Dist.CLIENT;
        if (mixinClassName.startsWith(SERVER_MIXIN_PREFIX)) return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
