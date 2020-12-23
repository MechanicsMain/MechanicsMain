package me.deecaad.weaponmechanics;

import me.deecaad.core.utils.ReflectionUtil;
import me.deecaad.core.utils.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Field;

public class Tools {

    private final Object a = "eyyyyy";
    private static final Object b = "ohhhhhhhhh";

    public static void main(String[] args) {

        Tools tools = new Tools();
        Field aField = ReflectionUtil.getField(Tools.class, "a");
        System.out.println(ReflectionUtil.invokeField(aField, tools));
        ReflectionUtil.setField(aField, tools, "testing testing 123");
        System.out.println(ReflectionUtil.invokeField(aField, tools));

        Field bField = ReflectionUtil.getField(Tools.class, "b");
        System.out.println(ReflectionUtil.invokeField(bField, null));
        ReflectionUtil.setField(bField, null, "testing again");
        System.out.println(ReflectionUtil.invokeField(aField, null));

        System.out.println(StringUtils.color("&#FFFFFF/&6test&#efefef&r"));
    }
    
    public static void entityHitBox() {
        EntityType[] types = EntityType.values();
        
        System.out.println("Entity_Hitboxes:");
        for (EntityType type : types) {
            if (!type.isAlive()) continue; // If it can be a livingEntity, I think
            System.out.println("  " + type.name() + ":");
            System.out.println("    " + "Horizontal_Entity: false");
            System.out.println("    " + "HEAD: 0.0");
            System.out.println("    " + "BODY: 0.0");
            System.out.println("    " + "ARMS: true");
            System.out.println("    " + "LEGS: 0.0");
            System.out.println("    " + "FEET: 0.0");
        }
    }

    public static void blockDamageData() {
        System.out.println("        Block_List:");
        for (Material mat : Material.values()) {
            if (mat.isLegacy() || !mat.isBlock() || mat.isAir()) continue;

            int durability = (int) (mat.getBlastResistance() + mat.getHardness()) + 1;
            if (durability > 18) {
                continue;
            }

            System.out.println("          - " + mat.name().toLowerCase() + "~" + durability);
        }
    }
}
