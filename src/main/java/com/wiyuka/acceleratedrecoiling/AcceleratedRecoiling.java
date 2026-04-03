package com.wiyuka.acceleratedrecoiling;

import com.wiyuka.acceleratedrecoiling.api.EntityAccessBridge;
import com.wiyuka.acceleratedrecoiling.commands.ToggleFoldCommand;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.listeners.ServerStop;
import com.wiyuka.acceleratedrecoiling.natives.CollisionMapData;
import com.wiyuka.acceleratedrecoiling.natives.JavaVanillaBackend;
import com.wiyuka.acceleratedrecoiling.natives.ParallelAABB;
import com.wiyuka.acceleratedrecoiling.natives.TempID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.entity.EntityTickList;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AcceleratedRecoiling extends JavaPlugin {
    public static Logger JUL_LOGGER = java.util.logging.Logger.getLogger("Accelerated Recoiling");

    public static SLF4JLike LOGGER;
    public class SLF4JLike {

        private final Logger logger;

        public SLF4JLike(Logger logger) {
            this.logger = logger;
        }

        private String format(String message, Object... args) {
            if (args == null || args.length == 0) return message;

            for (Object arg : args) {
                message = message.replaceFirst("\\{}", arg == null ? "null" : arg.toString());
            }
            return message;
        }

        public void info(String msg, Object... args) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, format(msg, args));
            }
        }

        public void warn(String msg, Object... args) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, format(msg, args));
            }
        }

        public void error(String msg, Object... args) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, format(msg, args));
            }
        }

        public void debug(String msg, Object... args) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, format(msg, args));
            }
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();

        LOGGER = new SLF4JLike(JUL_LOGGER);

        registerCommand("acceleratedrecoiling", new ToggleFoldCommand());
        ClassLoader appClassLoader = net.minecraft.world.entity.Entity.class.getClassLoader();

        try {
            initializeMixin(appClassLoader);
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static void initializeMixin(ClassLoader classLoader) throws NoSuchFieldException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
        initLivingEntityMixins(classLoader);
        initServerLevelMixin(classLoader);
    }

    public static void initServerLevelMixin(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        Class<?> mixinClass = net.minecraft.server.level.ServerLevel.class;

        Field hookField = mixinClass.getDeclaredField("CALLBACK_OBJECT");
        Field hookMethodField = mixinClass.getDeclaredField("CALLBACK_METHOD");
        hookField.setAccessible(true);
        hookMethodField.setAccessible(true);

        Object consumer = (Consumer<Object>) entityListIterable -> {
            if (!FoldConfig.enableEntityCollision) {
                return;
            }

            TempID.tickStart();
            if(JavaVanillaBackend.isSelected()) JavaVanillaBackend.tick((EntityTickList) entityListIterable);
            List<Entity> livingEntities = new ArrayList<>();

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

            ParallelAABB.handleEntityPush(livingEntities, 1.0E-3);
        };
        java.lang.reflect.Method logicMethod = consumer.getClass().getMethod("accept", Object.class);
        logicMethod.setAccessible(true);
        hookField.set(null, (Object) consumer);
        hookMethodField.set(null, (Object) logicMethod);
    }

    private static void initLivingEntityMixins(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException {

        Class<?> mixinClass = net.minecraft.world.entity.LivingEntity.class;
        Field hookField = mixinClass.getDeclaredField("CALLBACK_OBJECT");
        Field hookMethod = mixinClass.getDeclaredField("CALLBACK_METHOD");
        hookField.setAccessible(true);
        hookMethod.setAccessible(true);

        BiFunction<Object, Object, List<Object>> func = (levelObj, entityObj) -> {
            net.minecraft.world.level.Level level = (net.minecraft.world.level.Level) levelObj;
            Entity entity = (Entity) entityObj;

            if (!FoldConfig.enableEntityCollision || entity instanceof Player || level.isClientSide()) {
                return null;
            }

            if(JavaVanillaBackend.isSelected()) {
//                List<Entity> originalEntities = JavaVanillaBackend.getPushableEntities(entity, entity.getBoundingBox());
                return new ArrayList<>(JavaVanillaBackend.getPushableEntities(entity, entity.getBoundingBox()));
            }

            if (EntityAccessBridge.getDensity(entity) < FoldConfig.densityThreshold) {
                return null;
            }

            List<Entity> rawList = CollisionMapData.getCollisionList(entity, level);

            Predicate<? super Entity> pushablePredicate = EntitySelector.pushableBy(entity);

            List<Object> filteredList = new ArrayList<>();
            for (Entity e : rawList) {
                if (pushablePredicate.test(e)) {
                    filteredList.add(e);
                }
            }

            return filteredList;
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