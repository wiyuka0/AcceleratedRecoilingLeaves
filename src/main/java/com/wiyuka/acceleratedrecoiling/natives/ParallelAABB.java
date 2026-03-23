package com.wiyuka.acceleratedrecoiling.natives;

import com.wiyuka.acceleratedrecoiling.api.EntityAccessBridge;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

public class ParallelAABB {

    static boolean isInitialized = false;

    public static void handleEntityPush(final List<Entity> livingEntities, double inflate) {

        CollisionMapData.clear();


        double[] aabb = new double[livingEntities.size() * 6];
        double[] locations = new double[livingEntities.size() * 3];

        int index = 0;
        for (Entity entity : livingEntities) {
//            ICustomBB customBB = (ICustomBB) entity;
//            customBB.extractionBoundingBox(aabb, index * 6, inflate);
//            customBB.extractionPosition(locations, index * 3);
            Vec3 position = entity.position();
            locations[index * 3 + 0] = position.x;
            locations[index * 3 + 1] = position.y;
            locations[index * 3 + 2] = position.z;

            AABB boundingBox = entity.getBoundingBox();
            aabb[index * 6 + 0] = boundingBox.minX;
            aabb[index * 6 + 1] = boundingBox.minY;
            aabb[index * 6 + 2] = boundingBox.minZ;
            aabb[index * 6 + 3] = boundingBox.maxX;
            aabb[index * 6 + 4] = boundingBox.maxY;
            aabb[index * 6 + 5] = boundingBox.maxZ;
            index++;
        }


        int[] resultCounts = new int[1];

        PushResult result = nativePush(locations, aabb, resultCounts);

        if (result == null) return;

        index = 0;
        for (Entity entity : livingEntities) {
//            ICustomData customBB = (ICustomData) entity;
//            float density = result.density().getAtIndex(ValueLayout.JAVA_FLOAT, index);
//            customBB.setDensity(density);
            float density = result.getDensity(index);
            EntityAccessBridge.setDensity(entity, density);
            if (FoldConfig.debugDensity) {
                Component debugName = Component.literal("Density: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(String.format("%.2f", density))
                                .withStyle(ChatFormatting.YELLOW));

                entity.setCustomName(debugName);
                entity.setCustomNameVisible(true);
            }
//            else if (entity.hasCustomName() && entity.getCustomName().getString().startsWith("Density: ")) {
//                entity.setCustomName(null);
//                entity.setCustomNameVisible(false);
//            }
            index++;
        }

        for (int i = 0; i < resultCounts[0]; i++) {
//            int e1Index = result[i * 2];
//            int e2Index = result[i * 2 + 1];
//            int e1Index = result.A().getAtIndex(ValueLayout.JAVA_INT, i);
//            int e2Index = result.B().getAtIndex(ValueLayout.JAVA_INT, i);
            int e1Index = result.getA(i);
            int e2Index = result.getB(i);


            if (e1Index >= livingEntities.size() || e2Index >= livingEntities.size()) continue;

            Entity e1 = livingEntities.get(e1Index);
            Entity e2 = livingEntities.get(e2Index);

//            if(!e1.getBoundingBox().inflate(inflate).intersects(e2.getBoundingBox().inflate(inflate))) continue;

//            CollisionMapData.putCollision(e1.getUUID(), e2.getUUID());
            LivingEntity livingEntity;
            Entity entity;

            if(e1 instanceof LivingEntity) {
                livingEntity = (LivingEntity) e1;
                entity =  e2;
            } else if(e2 instanceof LivingEntity) {
                livingEntity = (LivingEntity) e2;
                entity = e1;
            } else continue;

            if (EntitySelector.pushableBy(livingEntity).test(entity)) {
                CollisionMapData.putCollision(TempID.getId(livingEntity), TempID.getId(entity));
            }
//            CollisionMapData.putCollision(livingEntity.getId(), entity.getId());
//            e1.doPush(e2);
//            e2.doPush(e1);

//            entityCollisionMap.computeIfAbsent(e1.getUUID().toString(), k -> new EntityData(e1, 0)).count++;
//            entityCollisionMap.computeIfAbsent(e2.getUUID().toString(), k -> new EntityData(e2, 0)).count++;
        }

//        entityCollisionMap.forEach((id, data) -> {
//            Entity entity = data.entity;
//            if (entity.level() instanceof ServerLevel serverLevel) {
//                int maxCollisionLimit = serverLevel.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
//                if (entity instanceof LivingEntity living && data.count >= maxCollisionLimit && maxCollisionLimit >= 0) {
//                    living.hurt(living.damageSources().cramming(), 6.0F);
//                }
//            }
//        });
    }

    public static PushResult nativePush(double[] positions, double[] aabbs, int[] resultSizeOut) {
        if(!isInitialized) {
            NativeInterface.initialize();
            isInitialized = true;
        }
        return NativeInterface.push(positions, aabbs, resultSizeOut);
    }
}

