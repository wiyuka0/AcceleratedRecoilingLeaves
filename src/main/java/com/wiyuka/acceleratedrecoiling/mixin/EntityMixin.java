package com.wiyuka.acceleratedrecoiling.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityMixin{

    @Unique
    public int _accelerated_recoiling_native_id_ = -1;

}