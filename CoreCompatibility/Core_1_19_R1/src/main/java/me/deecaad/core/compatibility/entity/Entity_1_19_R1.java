package me.deecaad.core.compatibility.entity;

import me.deecaad.core.MechanicsCore;
import me.deecaad.core.compatibility.equipevent.NonNullList_1_19_R1;
import me.deecaad.core.compatibility.equipevent.TriIntConsumer;
import me.deecaad.core.utils.LogLevel;
import me.deecaad.core.utils.ReflectionUtil;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.List;

// https://nms.screamingsandals.org/1.18.1/
public class Entity_1_19_R1 implements EntityCompatibility {

    static {
        if (ReflectionUtil.getMCVersion() != 19) {
            MechanicsCore.debug.log(
                    LogLevel.ERROR,
                    "Loaded " + Entity_1_19_R1.class + " when not using Minecraft 19",
                    new InternalError()
            );
        }
    }

    @Override
    public List generateNonNullList(int size, TriIntConsumer<org.bukkit.inventory.ItemStack, org.bukkit.inventory.ItemStack> consumer) {
        return new NonNullList_1_19_R1(size, consumer);
    }

    @Override
    public FakeEntity generateFakeEntity(Location location, EntityType type, Object data) {
        return new FakeEntity_1_19_R1(location, type, data);
    }
}