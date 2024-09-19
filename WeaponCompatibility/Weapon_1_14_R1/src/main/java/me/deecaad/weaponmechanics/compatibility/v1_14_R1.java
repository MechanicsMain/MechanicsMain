package me.deecaad.weaponmechanics.compatibility;

import me.deecaad.weaponmechanics.compatibility.scope.IScopeCompatibility;
import me.deecaad.weaponmechanics.compatibility.scope.Scope_1_14_R1;
import net.minecraft.server.v1_14_R1.DamageSource;
import net.minecraft.server.v1_14_R1.EntityLiving;
import net.minecraft.server.v1_14_R1.PacketPlayOutPosition;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class v1_14_R1 implements IWeaponCompatibility {

    private final Set<PacketPlayOutPosition.EnumPlayerTeleportFlags> RELATIVE_FLAGS = new HashSet<>(Arrays.asList(PacketPlayOutPosition.EnumPlayerTeleportFlags.X,
        PacketPlayOutPosition.EnumPlayerTeleportFlags.Y,
        PacketPlayOutPosition.EnumPlayerTeleportFlags.Z,
        PacketPlayOutPosition.EnumPlayerTeleportFlags.X_ROT,
        PacketPlayOutPosition.EnumPlayerTeleportFlags.Y_ROT));

    private final Set<PacketPlayOutPosition.EnumPlayerTeleportFlags> ABSOLUTE_FLAGS = new HashSet<>(Arrays.asList(PacketPlayOutPosition.EnumPlayerTeleportFlags.X,
        PacketPlayOutPosition.EnumPlayerTeleportFlags.Y,
        PacketPlayOutPosition.EnumPlayerTeleportFlags.Z));

    private final IScopeCompatibility scopeCompatibility;

    public v1_14_R1() {
        this.scopeCompatibility = new Scope_1_14_R1();
    }

    @NotNull @Override
    public IScopeCompatibility getScopeCompatibility() {
        return scopeCompatibility;
    }

    @Override
    public void modifyCameraRotation(Player player, float yaw, float pitch, boolean absolute) {
        pitch *= -1;
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutPosition(0, 0, 0, yaw, pitch, absolute ? ABSOLUTE_FLAGS : RELATIVE_FLAGS, 0));
    }

    @Override
    public void logDamage(LivingEntity victim, LivingEntity source, double health, double damage, boolean isMelee) {
        DamageSource damageSource;

        if (isMelee) {
            if (source instanceof Player) {
                damageSource = DamageSource.playerAttack(((org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer) source).getHandle());
            } else {
                damageSource = DamageSource.mobAttack(((CraftLivingEntity) source).getHandle());
            }
        } else {
            damageSource = DamageSource.projectile(null, ((CraftLivingEntity) source).getHandle());
        }

        EntityLiving nms = ((CraftLivingEntity) victim).getHandle();
        nms.combatTracker.trackDamage(damageSource, (float) damage, (float) health);
        nms.setLastDamager(((CraftLivingEntity) source).getHandle());
    }

    @Override
    public EntityDamageByEntityEvent newEntityDamageByEntityEvent(org.bukkit.entity.LivingEntity victim, org.bukkit.entity.LivingEntity source, double damage, boolean isMelee) {
        return new EntityDamageByEntityEvent(
            source,
            victim,
            isMelee ? EntityDamageByEntityEvent.DamageCause.ENTITY_ATTACK : EntityDamageByEntityEvent.DamageCause.PROJECTILE,
            damage);
    }

    @Override
    public void setKiller(LivingEntity victim, Player killer) {
        ((CraftLivingEntity) victim).getHandle().killer = ((CraftPlayer) killer).getHandle();
    }
}