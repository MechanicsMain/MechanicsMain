package me.deecaad.weaponmechanics.weapon.damage;

import com.cjcrafter.foliascheduler.util.MinecraftVersions;
import me.deecaad.core.compatibility.CompatibilityAPI;
import me.deecaad.core.file.Configuration;
import me.deecaad.core.utils.NumberUtil;
import me.deecaad.core.utils.RandomUtil;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.compatibility.WeaponCompatibilityAPI;
import me.deecaad.weaponmechanics.utils.MetadataKey;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.LazyMetadataValue;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static me.deecaad.weaponmechanics.WeaponMechanics.debug;
import static me.deecaad.weaponmechanics.WeaponMechanics.getBasicConfigurations;

public class DamageUtil {

    /**
     * Do not let anyone instantiate this class
     */
    private DamageUtil() {
    }

    /**
     * @param cause The shooter that caused the damage.
     * @param victim The victim being damaged.
     * @param damage The amount of damage to apply
     * @return true if damage was cancelled
     */
    public static boolean apply(LivingEntity cause, LivingEntity victim, double damage) {
        Configuration config = getBasicConfigurations();

        // Skip armor stands for better plugin compatibility
        if (victim instanceof ArmorStand armorStand) {

            if (config.getBoolean("Damage.Ignore_Armor_Stand.Always"))
                return true;
            if (config.getBoolean("Damage.Ignore_Armor_Stand.Marker") && armorStand.isMarker())
                return true;
            if (config.getBoolean("Damage.Ignore_Armor_Stand.Invisible") && armorStand.isInvisible())
                return true;
        }

        if (victim.isInvulnerable() || victim.isDead())
            return true;

        // Make sure the player is not in creative or spectator, can only damage survival/adventure
        if (victim instanceof Player player) {
            GameMode gamemode = player.getGameMode();

            if (gamemode == GameMode.CREATIVE || gamemode == GameMode.SPECTATOR)
                return true;
        }

        // Use enderman teleport API added in 1.20.1
        else if (victim instanceof Enderman enderman && MinecraftVersions.TRAILS_AND_TAILS.get(1).isAtLeast()) {

            // 64 is the value minecraft uses
            int teleportAttempts = WeaponMechanics.getBasicConfigurations().getInt("Damage.Enderman_Teleport_Attempts", 64);

            boolean isTeleported = false;
            for (int i = 0; i < teleportAttempts && !isTeleported; i++) {

                // Teleport randomly if the enderman is calm, otherwise assume their
                // target is the shooter, and teleport towards the shooter.
                if (enderman.getTarget() == null)
                    isTeleported = enderman.teleport();
                else
                    isTeleported = enderman.teleportTowards(cause);
            }

            // When the enderman does not teleport away, this is probably because
            // the user disabled the enderman teleportation in config.yml (set
            // attempts to 0), so we should damage the enderman instead.
            if (isTeleported)
                return true;
        }

        // Skip damaging if possible
        if (damage < 0)
            damage = 0;

        if (getBasicConfigurations().getBoolean("Damage.Use_Vanilla_Damaging", false)) {
            if (damage == 0)
                return true;

            // VANILLA_DAMAGE is used to make sure we don't do melee trigger checks
            // on EntityDamageByEntityEvent. null doesn't matter, the metadata key
            // is still applied to the entity.
            MetadataKey.VANILLA_DAMAGE.set(victim, null);

            victim.damage(damage, cause);

            // If the EntityDamageByEntityEvent was cancelled, then we should skip
            // everything else.
            if (MetadataKey.CANCELLED_DAMAGE.has(victim)) {
                MetadataKey.CANCELLED_DAMAGE.remove(victim);
                return true;
            }

            // Vanilla thing to allow constant hits from projectiles
            victim.setNoDamageTicks(0);
            return false;
        }

        // For compatibility with plugins that only set the damage to 0.0...
        double tempDamage = damage;

        // try-catch New damage source API added in later 1.20.4 versions
        EntityDamageByEntityEvent entityDamageByEntityEvent;
        try {
            victim.setMetadata("doing-weapon-damage", new LazyMetadataValue(WeaponMechanics.getPlugin(), () -> true));
            entityDamageByEntityEvent = WeaponCompatibilityAPI.getWeaponCompatibility().newEntityDamageByEntityEvent(victim, cause, damage, true);
            Bukkit.getPluginManager().callEvent(entityDamageByEntityEvent);
            victim.removeMetadata("doing-weapon-damage", WeaponMechanics.getPlugin());
            if (entityDamageByEntityEvent.isCancelled())
                return true;
        } catch (LinkageError ex) {
            debug.error("You are using an outdated version of Spigot 1.20.4. Please update to the latest version.",
                "This is required for the new damage source API to work.",
                "Detected version: " + MinecraftVersions.getCurrent(), "");
            return true;
        }

        // Doing getDamage() is enough since only BASE modifier is used in event call above ^^
        damage = entityDamageByEntityEvent.getDamage();
        if (tempDamage != damage && damage == 0.0) {
            // If event changed damage, and it's now 0.0, consider this as cancelled damage event
            return true;
        }

        // Calculate the amount of damage to absorption hearts, and
        // determine how much damage is left over to deal to the victim
        double absorption = victim.getAbsorptionAmount();
        double absorbed = Math.max(0, absorption - damage);
        victim.setAbsorptionAmount(absorbed);
        damage = Math.max(damage - absorption, 0);

        double oldHealth = victim.getHealth();

        // Apply any remaining damage to the victim, and handle internals
        WeaponCompatibilityAPI.getWeaponCompatibility().logDamage(victim, cause, oldHealth, damage, false);
        if (cause instanceof Player player) {
            WeaponCompatibilityAPI.getWeaponCompatibility().setKiller(victim, player);
        }

        // Visual red flash
        WeaponCompatibilityAPI.getWeaponCompatibility().playHurtAnimation(victim);

        // Spigot api things
        victim.setLastDamage(damage);
        // victim.setLastDamageCause(entityDamageByEntityEvent);

        double newHealth = NumberUtil.clamp(oldHealth - damage, 0, victim.getAttribute(Attribute.MAX_HEALTH).getValue());
        boolean killed = newHealth <= 0.0;
        boolean resurrected = false;

        // Try use totem of undying
        if (killed) {
            resurrected = CompatibilityAPI.getEntityCompatibility().tryUseTotemOfUndying(victim);
            killed = !resurrected;
        }

        // When the victim is resurrected via a totem, their health will already be set to 1.0
        if (!resurrected)
            victim.setHealth(newHealth);

        // Statistics
        if (victim instanceof Player player) {
            if (MinecraftVersions.UPDATE_AQUATIC.isAtLeast() && absorbed >= 0.1)
                player.incrementStatistic(Statistic.DAMAGE_ABSORBED, Math.round((float) absorbed * 10));
            if (damage >= 0.1)
                player.incrementStatistic(Statistic.DAMAGE_TAKEN, Math.round((float) damage * 10));
            if (killed)
                player.incrementStatistic(Statistic.ENTITY_KILLED_BY, cause.getType());
        }
        if (cause instanceof Player player) {
            if (MinecraftVersions.UPDATE_AQUATIC.isAtLeast() && absorbed >= 0.1)
                player.incrementStatistic(Statistic.DAMAGE_DEALT_ABSORBED, Math.round((float) absorbed * 10));
            if (damage >= 0.1)
                player.incrementStatistic(Statistic.DAMAGE_DEALT, Math.round((float) damage * 10));
            if (killed) {
                player.incrementStatistic(Statistic.KILL_ENTITY, victim.getType());

                // In newer versions (probably 1.13, but only confirmed in 1.18.2+),
                // these statistics are automatically tracked.
                if (!MinecraftVersions.UPDATE_AQUATIC.isAtLeast()) {
                    if (victim.getType() == EntityType.PLAYER)
                        player.incrementStatistic(Statistic.PLAYER_KILLS);
                    else
                        player.incrementStatistic(Statistic.MOB_KILLS);
                }
            }
        }

        return false;
    }

    public static void damageArmor(LivingEntity victim, int amount) {
        damageArmor(victim, amount, null);
    }

    public static void damageArmor(LivingEntity victim, int amount, @Nullable DamagePoint point) {

        // If the damage amount is 0, we can skip the calculations
        if (amount <= 0)
            return;

        // Stores which armors should be damaged
        EntityEquipment equipment = victim.getEquipment();
        if (equipment == null)
            return;

        if (point == null) {
            damage(equipment, EquipmentSlot.HEAD, amount);
            damage(equipment, EquipmentSlot.CHEST, amount);
            damage(equipment, EquipmentSlot.LEGS, amount);
            damage(equipment, EquipmentSlot.FEET, amount);
        } else {
            switch (point) {
                case HEAD -> damage(equipment, EquipmentSlot.HEAD, amount);
                case BODY, ARMS -> damage(equipment, EquipmentSlot.CHEST, amount);
                case LEGS -> damage(equipment, EquipmentSlot.LEGS, amount);
                case FEET -> damage(equipment, EquipmentSlot.FEET, amount);
                default -> throw new IllegalArgumentException("Unknown point: " + point);
            }
        }
    }

    private static void damage(EntityEquipment equipment, EquipmentSlot slot, int amount) {
        ItemStack armor = switch (slot) {
            case HEAD -> equipment.getHelmet();
            case CHEST -> equipment.getChestplate();
            case LEGS -> equipment.getLeggings();
            case FEET -> equipment.getBoots();
            default -> throw new IllegalArgumentException("Invalid slot: " + slot);
        };

        // All items implement Damageable (since Spigot is stupid). We use this check
        // to see if an item is *actually* damageable.
        if (armor == null || "AIR".equals(armor.getType().name()) || (MinecraftVersions.UPDATE_AQUATIC.isAtLeast() && armor.getType().getMaxDurability() == 0))
            return;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null)
            return;

        // Do not attempt to damage armor that is unbreakable
        if (meta.isUnbreakable())
            return;

        // Formula taken from Unbreaking enchant code
        int level = meta.getEnchantLevel(Enchantment.UNBREAKING);
        boolean skipDamage = !RandomUtil.chance(0.6 + 0.4 / (level + 1));
        if (skipDamage)
            return;

        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + amount);
            armor.setItemMeta(meta);

            if (damageable.getDamage() >= armor.getType().getMaxDurability())
                armor.setAmount(0);
        }

        // Getting an ItemStack from an EntityEquipment copies the item... we
        // need to set the item.
        switch (slot) {
            case HEAD -> equipment.setHelmet(armor);
            case CHEST -> equipment.setChestplate(armor);
            case LEGS -> equipment.setLeggings(armor);
            case FEET -> equipment.setBoots(armor);
        }
    }

    /**
     * @param cause the cause entity (shooter of projectile)
     * @param victim the victim
     * @return true only if cause can harm victim
     */
    public static boolean canHarmScoreboardTeams(LivingEntity cause, LivingEntity victim) {

        // Owner invulnerability is handled separately.
        if (cause.equals(victim))
            return true;

        // Only check scoreboard teams for players
        if (cause.getType() != EntityType.PLAYER || victim.getType() != EntityType.PLAYER)
            return true;

        Scoreboard shooterScoreboard = ((Player) cause).getScoreboard();
        if (shooterScoreboard == null)
            return true;

        Set<Team> teams = shooterScoreboard.getTeams();
        if (teams == null || teams.isEmpty())
            return true;

        for (Team team : teams) {
            Set<String> entries = team.getEntries();

            // Seems like this has to be also checked...
            if (!entries.contains(cause.getName()))
                continue;

            // If not in same team -> continue
            if (!entries.contains(victim.getName()))
                continue;

            // Now we know they're in same team.
            // -> If friendly is not enabled
            // --> they can't harm each other
            if (!team.allowFriendlyFire()) {
                // This approach only checks first same team WHERE friendly fire is enabled
                return false;
            }
        }

        return true;
    }
}
