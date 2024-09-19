package me.deecaad.weaponmechanics.compatibility.scope;

import com.comphenix.protocol.events.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.potion.PotionEffectType;

public class Scope_1_17_R1 implements IScopeCompatibility {

    @Override
    public void updateAbilities(org.bukkit.entity.Player player) {
        ServerPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        entityPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(entityPlayer.getAbilities()));
    }

    @Override
    public void addNightVision(org.bukkit.entity.Player player) {
        // 6000 = 5min
        ClientboundUpdateMobEffectPacket entityEffect = new ClientboundUpdateMobEffectPacket(-player.getEntityId(), new MobEffectInstance(MobEffect.byId(PotionEffectType.NIGHT_VISION.getId()), 6000,
            2));
        ((CraftPlayer) player).getHandle().connection.send(entityEffect);
    }

    @Override
    public void removeNightVision(org.bukkit.entity.Player player) {
        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {

            ServerPlayer entityPlayer = ((CraftPlayer) player).getHandle();

            // Simply remove the entity effect
            ClientboundRemoveMobEffectPacket removeEntityEffect = new ClientboundRemoveMobEffectPacket(player.getEntityId(), MobEffect.byId(PotionEffectType.NIGHT_VISION.getId()));
            entityPlayer.connection.send(removeEntityEffect);

            // resend the existing one
            MobEffectInstance mobEffect = entityPlayer.getEffect(MobEffect.byId(PotionEffectType.NIGHT_VISION.getId()));
            ClientboundUpdateMobEffectPacket entityEffect = new ClientboundUpdateMobEffectPacket(player.getEntityId(), mobEffect);
            ((CraftPlayer) player).getHandle().connection.send(entityEffect);
            return;
        }

        // Simply remove the entity effect
        ClientboundRemoveMobEffectPacket removeEntityEffect = new ClientboundRemoveMobEffectPacket(player.getEntityId(), MobEffect.byId(PotionEffectType.NIGHT_VISION.getId()));
        ((CraftPlayer) player).getHandle().connection.send(removeEntityEffect);
    }

    @Override
    public boolean isRemoveNightVisionPacket(PacketEvent event) {
        // 16 = night vision
        return ((ClientboundRemoveMobEffectPacket) event.getPacket().getHandle()).getEffect() == MobEffect.byId(16);
    }
}