package com.wiyuka.acceleratedrecoiling;

import com.wiyuka.acceleratedrecoiling.commands.ToggleFoldCommand;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.listeners.ServerStop;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapData;
import com.wiyuka.acceleratedrecoiling.natives.ParallelAABB;
import com.wiyuka.acceleratedrecoiling.natives.TempID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.level.entity.EntityTickList;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class AcceleratedRecoiling extends JavaPlugin {
    public static Logger LOGGER = java.util.logging.Logger.getLogger("Accelerated Recoiling");
    @Override
    public void onEnable() {
        super.onEnable();

        registerCommand("acceleratedrecoiling", new ToggleFoldCommand());
        ClassLoader appClassLoader = net.minecraft.world.entity.Entity.class.getClassLoader();

        try {
            initializeMixin(appClassLoader);
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    private static void initializeMixin(ClassLoader classLoader) throws NoSuchFieldException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
        initServerLevelMixin(classLoader);
        initLivingEntityMixin(classLoader);
    }

    public static void initLivingEntityMixin(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException {

        Class<?> mixinClass = net.minecraft.server.level.ServerLevel.class;
//        Class<?> mixinClass = Class.forName("com.wiyuka.acceleratedrecoiling.mixin.LivingEntityMixin", false, classLoader);

        Field hookField = mixinClass.getDeclaredField("CALLBACK_OBJECT");
        Field hookMethodField = mixinClass.getDeclaredField("CALLBACK_METHOD");
        hookField.setAccessible(true);
        hookMethodField.setAccessible(true);

        Object consumer = new Consumer<Object>() {
            @Override
            public void accept(Object entityListIterable) {
                if (!FoldConfig.enableEntityCollision) {
                    return;
                }

                TempID.tickStart();
                List<Entity> livingEntities = new ArrayList<>();
                // List<Player> playerEntities = new ArrayList<>(); // 如果你需要这个

                // 3. 遍历 Mixin 传过来的 entityTickList
                // 因为 EntityTickList 实现了 Iterable<Entity>，所以可以直接 foreach
//            for (Entity entity : entityListIterable) {
//                if (!entity.isRemoved()) {
//                    if (entity instanceof Player) {
//                        // playerEntities.add((Player) entity);
//                    } else if (entity instanceof LivingEntity) {
//                        livingEntities.add((LivingEntity) entity);
//                    }
//                }
//            }
                ((EntityTickList)entityListIterable).forEach(entity -> {
                    if (!entity.isRemoved()) {
                        if (entity instanceof Player) {
                            // playerEntities.add((Player) entity);
                        } else if (entity instanceof Entity) {
                            livingEntities.add(entity);
                        }
                    }
                    TempID.addEntity(entity);
                });

                // 4. 调用你的并行处理逻辑
                // 此时 ParallelAABB 类肯定是已加载的，因为我们在插件内部
                ParallelAABB.handleEntityPush(livingEntities, 1.0E-3);
            }
        };
        java.lang.reflect.Method logicMethod = consumer.getClass().getMethod("accept", Object.class);
        logicMethod.setAccessible(true);
        hookField.set(null, (Object) consumer);
        hookMethodField.set(null, (Object) logicMethod);
    }

    private static void initServerLevelMixin(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException {

        Class<?> mixinClass = net.minecraft.world.entity.LivingEntity.class;
        Field hookField = mixinClass.getDeclaredField("CALLBACK_OBJECT");
        Field hookMethod = mixinClass.getDeclaredField("CALLBACK_METHOD");
        hookField.setAccessible(true);
        hookMethod.setAccessible(true);

        BiFunction<Object, Object, List<Object>> func = new BiFunction<>() {
            @Override
            public List<Object> apply(Object levelObj, Object entityObj) {
                net.minecraft.world.level.Level level = (net.minecraft.world.level.Level) levelObj;
                net.minecraft.world.entity.Entity entity = (net.minecraft.world.entity.Entity) entityObj;
                if (FoldConfig.enableEntityCollision
                        && !(entity instanceof net.minecraft.world.entity.player.Player)
                        && !level.isClientSide) {
//                    return CollisionMapData.getCollisionList(entity, level).stream().map(obj -> (Object) obj).collect(Collectors.toList());
                    @SuppressWarnings("unchecked")
                    List<Object> castedList = (List<Object>) (List<?>) CollisionMapData.getCollisionList(entity, level);
                    return castedList;
                }
                return null;
            }
        };

        java.lang.reflect.Method logicMethod = func.getClass().getMethod("apply", Object.class, Object.class);

        logicMethod.setAccessible(true);

        hookField.set(null, (Object) func);
        hookMethod.set(null, (Object) logicMethod);
    }

    @Override
    public void onDisable() {
        ServerStop.onServerStop();
    }
}