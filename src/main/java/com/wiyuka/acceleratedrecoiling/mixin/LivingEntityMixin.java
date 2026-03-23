package com.wiyuka.acceleratedrecoiling.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mixin(value = LivingEntity.class, remap = false)
public class LivingEntityMixin {

    @Unique private static Object CALLBACK_OBJECT = null;
    @Unique private static Method CALLBACK_METHOD = null;

    @WrapOperation(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;doPush(Lnet/minecraft/world/entity/Entity;)V")
    )
    private void doPushVerify(LivingEntity instance, Entity entity, Operation<Void> original){
        if(instance.getBoundingBox().intersects(entity.getBoundingBox())){
            original.call(instance, entity);
        }
    }

    @WrapOperation(
            method = "pushEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getPushableEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> replace(Level instance, Entity entity, AABB boundingBox, Operation<List<Entity>> original) {
        if (CALLBACK_OBJECT != null && CALLBACK_METHOD != null) {

//            List<Object> result = ((BiFunction<Object, Object, List<Object>>)CALLBACK).apply(instance, entity);
//            try {
//                Object object = e(CACALLBACK_METHOD.invokLLBACK_OBJECT, instance, entity);
            Object object = ((BiFunction) CALLBACK_OBJECT).apply(instance, entity);

            if(object == null) {
                return original.call(instance, entity, boundingBox);
            }
            return (List<Entity>) object;
//            } catch (IllegalAccessEdxception | InvocationTargetException e) {
//                throw new RuntimeException(e);
//            }


//            if (result != null) {
//                return result.stream().map(obj -> (Entity)obj).collect(Collectors.toList());
//            }
        }


        return original.call(instance, entity, boundingBox);
    }

//    @WrapOperation(
//            method = "pushEntities",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/world/level/Level;getPushableEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
//            )
//    )
//    private List<Entity> replace(Level instance, Entity entity, AABB boundingBox, Operation<List<Entity>> original) {
////        Set<UUID> entities = CollisionMapTemp.get(entity.getUUID());
////        if(entities == null) return Collections.emptyList();
////        List<Entity> result = new ArrayList<>();
////        for (UUID uuid : entities) {
////            Entity entity1 = instance.getEntity(uuid);
////            if (entity1 == null) continue;
////            result.add(entity1);
////        }
////        return result;
//        if(FoldConfig.enableEntityCollision && !(entity instanceof Player) && !entity.level().isClientSide)
//            return CollisionMapData.replace1(entity, instance, false);
//        else
//            return original.call(instance, entity, boundingBox);
//    }


//    @Inject(
//            method = "aiStep",
//            at = @At(
//                    "HEAD"
//            ),
//            cancellable = true
//    )
//    private void aiStep(final CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//        if(self instanceof Player) return;
//        ci.cancel();
//
//    }
//    @Inject(
//            method = "serverAiStep",
//            at = @At(
//                    "HEAD"
//            ),
//            cancellable = true
//    )
//    private void serverAiStep(final CallbackInfo ci) {
//        LivingEntity self = (LivingEntity) (Object) this;
//
//        if(self instanceof Player) return;
//        ci.cancel();
//    }
//    @Redirect(
//            method = "aiStep",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/world/entity/LivingEntity;pushEntities()V"
//            ),
//            cancellable = true
//    )
//    public void pushEntities(LivingEntity livingEntity) {
//        if (!ParallelAABB.useFold || livingEntity instanceof Player) {
//            pushEntities(livingEntity);
//        }
//    }
}