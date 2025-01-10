package me.deecaad.weaponmechanics.weapon.shoot.recoil;

import com.cjcrafter.foliascheduler.TaskImplementation;
import me.deecaad.core.utils.NumberUtil;
import me.deecaad.core.utils.RandomUtil;
import me.deecaad.weaponmechanics.compatibility.IWeaponCompatibility;
import me.deecaad.weaponmechanics.compatibility.WeaponCompatibilityAPI;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponRecoilEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static me.deecaad.core.utils.NumberUtil.lerp;
import static me.deecaad.core.utils.NumberUtil.moveTowards;

/**
 * Completely new AAA-like recoil controller that ensures
 * negative deltas are applied during recovery.
 */
public class RecoilController implements Consumer<TaskImplementation<Void>> {

    private final @NotNull Player player;

    // Properties from the recoil profile
    private float damping = 0f;
    private float smoothingFactor = 0f;
    private float dampingRecovery = 0f;
    private float recoilSpeed = 0f;

    // Current "accumulated" recoil (the internal model)
    private float currentRecoilX = 0f;
    private float currentRecoilY = 0f;
    private float currentRecoilFOV = 0f;

    // The "goal" recoil
    private float targetRecoilX = 0f;
    private float targetRecoilY = 0f;
    private float targetRecoilFOV = 0f;


    public RecoilController(@NotNull Player player) {
        this.player = player;
    }

    public void onShotFired(
        @NotNull RecoilProfile recoil,
        @Nullable String weaponTitle,
        @Nullable ItemStack weaponStack,
        @Nullable Player shooter,
        @Nullable EquipmentSlot hand
    ) {
        // random approach around (mean ± variance)
        float dx;
        float dy;
        float dfov;
        float maxAccum;

        if (weaponTitle != null && weaponStack != null && shooter != null) {
            WeaponRecoilEvent event = new WeaponRecoilEvent(weaponTitle, weaponStack, shooter, hand, recoil);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }

            damping = event.getDamping();
            smoothingFactor = event.getSmoothingFactor();
            dampingRecovery = event.getDampingRecovery();
            recoilSpeed = event.getRecoilSpeed();

            dx = RandomUtil.variance(event.getRecoilMeanX(), event.getRecoilVarianceX());
            dy = RandomUtil.variance(event.getRecoilMeanY(), event.getRecoilVarianceY());
            dfov = 0.0f;
            maxAccum = event.getMaxRecoilAccum();
        } else {
            damping = recoil.getDamping();
            smoothingFactor = recoil.getSmoothingFactor();
            dampingRecovery = recoil.getDampingRecovery();
            recoilSpeed = recoil.getRecoilSpeed();

            dx = RandomUtil.variance(recoil.getRecoilMeanX(), recoil.getRecoilVarianceX());
            dy = RandomUtil.variance(recoil.getRecoilMeanY(), recoil.getRecoilVarianceY());
            dfov = 0.0f;
            maxAccum = recoil.getMaxRecoilAccum();
        }

        targetRecoilX += dx;
        targetRecoilY += dy;
        targetRecoilFOV += dfov;

        float lengthXY = (float) Math.sqrt(targetRecoilX * targetRecoilX + targetRecoilY * targetRecoilY);
        if (lengthXY > maxAccum) {
            float scale = maxAccum / lengthXY;
            targetRecoilX *= scale;
            targetRecoilY *= scale;
        }
    }

    @Override
    public void accept(TaskImplementation<Void> task) {
        // Safety check
        if (!player.isOnline() || player.isDead()) {
            return;
        }

        // Store the old recoil values
        float oldX = currentRecoilX;
        float oldY = currentRecoilY;
        float oldFov = currentRecoilFOV;

        // DAMPING - reduce target recoil each tick
        targetRecoilX *= (1.0f - damping);
        targetRecoilY *= (1.0f - damping);
        targetRecoilFOV *= (1.0f - damping);

        // SMOOTHING - interpolate current recoil to target recoil
        currentRecoilX = lerp(currentRecoilX, targetRecoilX, smoothingFactor);
        currentRecoilY = lerp(currentRecoilY, targetRecoilY, smoothingFactor);
        currentRecoilFOV = lerp(currentRecoilFOV, targetRecoilFOV, smoothingFactor);

        // RECOVERY - pull the recoil back towards zero
        currentRecoilX = moveTowards(currentRecoilX, 0f, dampingRecovery);
        currentRecoilY = moveTowards(currentRecoilY, 0f, dampingRecovery);
        currentRecoilFOV = moveTowards(currentRecoilFOV, 0f, dampingRecovery);

        // Now we have updated recoil in currentRecoilX/Y/FOV

        // Use the difference between old and new for the final camera delta
        float deltaYaw = (currentRecoilX - oldX) * recoilSpeed;
        float deltaPitch = (currentRecoilY - oldY) * recoilSpeed;
        float deltaFov = (currentRecoilFOV - oldFov) * recoilSpeed;

        // If we actually have a non-trivial delta, apply it
        if (!NumberUtil.approximately(deltaYaw, 0f, 0.01f) || !NumberUtil.approximately(deltaPitch, 0f, 0.01f)) {
            IWeaponCompatibility compat = WeaponCompatibilityAPI.getWeaponCompatibility();
            compat.modifyCameraRotation(player, deltaYaw, deltaPitch, false);
        }
    }
}
