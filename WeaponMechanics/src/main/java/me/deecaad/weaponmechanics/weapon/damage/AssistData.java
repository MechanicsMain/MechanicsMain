package me.deecaad.weaponmechanics.weapon.damage;

import me.deecaad.core.file.Configuration;
import me.deecaad.core.file.IValidator;
import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.utils.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static me.deecaad.weaponmechanics.WeaponMechanics.getBasicConfigurations;

public class AssistData implements IValidator {

    private final Map<UUID, Map<String, DamageInfo>> players;

    public AssistData() {
        this.players = new HashMap<>();
    }

    public void logDamage(Player shooter, String weaponTitle, ItemStack weaponStack, double amount) {
        UUID uuid = shooter.getUniqueId();
        players.putIfAbsent(uuid, new HashMap<>());
        players.get(uuid).compute(weaponTitle, (key, value) -> value == null ? new DamageInfo(amount, weaponStack) : value.add(amount, weaponStack));
    }

    @Nullable
    public Map<Player, Map<String, DamageInfo>> getAssists(Player killer) {
        if (players.isEmpty()) return null;

        Map<Player, Map<String, DamageInfo>> assists = new HashMap<>();

        double requiredDamageAmount = getBasicConfigurations().getDouble("Assists_Event.Required_Damage_Amount", 0);
        int timer = getBasicConfigurations().getInt("Assists_Event.Timer", 0);

        players.forEach((uuid, value) -> {
            if (killer != null && uuid.equals(killer.getUniqueId())) return;

            Player playerByUuid = Bukkit.getPlayer(uuid);
            // Might be null if damager has quit
            if (playerByUuid == null) return;

            value.forEach((weaponTitle, damageInfo) -> {
                if (requiredDamageAmount != 0 && damageInfo.damage < requiredDamageAmount) return;
                if (timer != 0 && NumberUtil.hasMillisPassed(damageInfo.lastHitTime, timer)) return;

                assists.putIfAbsent(playerByUuid, new HashMap<>());
                assists.get(playerByUuid).put(weaponTitle, damageInfo);
            });
        });

        return assists.isEmpty() ? null : assists;
    }

    public static class DamageInfo {

        private long lastHitTime;
        private double damage;
        private ItemStack weaponStack;

        public DamageInfo(double damage, ItemStack weaponStack) {
            this.lastHitTime = System.currentTimeMillis();
            this.damage = damage;
            this.weaponStack = weaponStack;
        }

        private DamageInfo add(double damage, ItemStack weaponStack) {
            this.lastHitTime = System.currentTimeMillis();
            this.damage += damage;
            this.weaponStack = weaponStack;
            return this;
        }

        public double getDamage() {
            return damage;
        }

        public ItemStack getWeaponStack() {
            return weaponStack;
        }

        public long getLastHitTime() {
            return lastHitTime;
        }

        @Override
        public String toString() {
            return "DamageInfo{" +
                    "lastHitTime=" + lastHitTime +
                    ", damage=" + damage +
                    ", weaponStack=" + weaponStack.getType() +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "AssistData{" +
                "players=" + players +
                '}';
    }

    @Override
    public String getKeyword() {
        return "Assists_Event";
    }

    @Override
    public void validate(Configuration configuration, SerializeData data) throws SerializerException {
        int timer = data.of("Timer").assertPositive().getInt(100);
        if (timer != 0) {
            // Convert to millis
            configuration.set(data.key + ".Timer", timer * 50);
        }

        double damageAmount = data.of("Required_Damage_Amount").assertPositive().getDouble(0);

        if (data.of("Enable").getBool(true) && timer == 0 && damageAmount == 0) {
            throw data.exception("", "When using assists, make sure to either use Timer or Required_Damage_Amount");
        }
    }
}