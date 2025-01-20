package me.deecaad.core.mechanics.defaultmechanics;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.file.serializers.AnyVectorProvider;
import me.deecaad.core.file.serializers.ItemSerializer;
import me.deecaad.core.file.serializers.VectorProvider;
import me.deecaad.core.file.serializers.VectorSerializer;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.utils.EntityTransform;
import me.deecaad.core.utils.ImmutableVector;
import me.deecaad.core.utils.Quaternion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Drops a real item at the target location.
 */
public class DropItemMechanic extends Mechanic {

    private ItemStack item;
    private VectorProvider velocity;

    /**
     * Default constructor for serializers.
     */
    public DropItemMechanic() {
    }

    public DropItemMechanic(ItemStack item, VectorProvider velocity) {
        this.item = item;
        this.velocity = velocity;
    }

    @Override
    public void use0(CastData cast) {
        World world = cast.getTargetWorld();
        Location spawnPosition = cast.hasTargetLocation() ? cast.getTargetLocation() : cast.getTarget().getEyeLocation();
        if (world == null)
            return;

        world.dropItem(spawnPosition, item, itemEntity -> {
            EntityTransform localTransform = cast.getTarget() == null ? null : new EntityTransform(cast.getTarget());
            Quaternion localRotation = localTransform == null ? null : localTransform.getLocalRotation();
            itemEntity.setVelocity(velocity.provide(localRotation).multiply(1.0 / 20.0));
        });
    }

    @Override
    public String getKeyword() {
        return "Drop_Item";
    }

    @Override
    public @NotNull Mechanic serialize(@NotNull SerializeData data) throws SerializerException {

        ItemStack item = new ItemSerializer().serialize(data);
        VectorProvider zero = new AnyVectorProvider(false, new ImmutableVector());
        VectorProvider velocity = data.of("Velocity").serialize(VectorSerializer.class).orElse(zero);

        return applyParentArgs(data, new DropItemMechanic(item, velocity));
    }
}
