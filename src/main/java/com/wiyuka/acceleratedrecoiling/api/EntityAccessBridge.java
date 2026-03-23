package com.wiyuka.acceleratedrecoiling.api;

import net.minecraft.world.entity.Entity;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class EntityAccessBridge {
    private static final VarHandle NATIVE_ID_HANDLE;
    private static final VarHandle DENSITY_HANDLE;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup());

            NATIVE_ID_HANDLE = lookup.findVarHandle(Entity.class, "_accelerated_recoiling_native_id_", int.class);
            DENSITY_HANDLE = lookup.findVarHandle(Entity.class, "_accelerated_recoiling_density_", float.class);

        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getNativeId(Entity entity) {
        return (int) NATIVE_ID_HANDLE.get(entity);
    }

    public static void setNativeId(Entity entity, int id) {
        NATIVE_ID_HANDLE.set(entity, id);
    }

    public static float getDensity(Entity entity) {
        return (float) DENSITY_HANDLE.get(entity);
    }

    public static void setDensity(Entity entity, float density) {
        DENSITY_HANDLE.set(entity, density);
    }
}
