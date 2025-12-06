package com.wiyuka.acceleratedrecoiling.natives;

import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CollisionMapData {
//    private static final Int2ObjectOpenHashMap<IntArrayList> collisionMap = new Int2ObjectOpenHashMap<>(10000);

    private static IntArrayList[] collisionMap = new IntArrayList[10000];

    public static void putCollision(int idA, int idB) {
        addSingle(idA, idB);
        addSingle(idB, idA);
    }

    private static void addSingle(int source, int target) {
        if(collisionMap.length <= source) {
            resize((int) (collisionMap.length * 1.5));
        }
        IntArrayList list = collisionMap[source];
        if (list == null) {
            list = new IntArrayList(FoldConfig.maxCollision * 2);
            collisionMap[source] = list;
//            collisionMap.put(source, list);
        }
        list.add(target);
    }

    public static void resize(int newSize) {
        IntArrayList[] newCollisionMap = new IntArrayList[newSize];


        for (int oldIndex = 0; oldIndex < collisionMap.length; oldIndex++) {
            IntArrayList list = collisionMap[oldIndex];

            if (list != null) {
                int idA = oldIndex;

                for (int i = 0; i < list.size(); i++) {
                    int idB = list.get(i);
                    if (idA < newSize) {
                        IntArrayList newList = newCollisionMap[idA];
                        if (newList == null) {
                            newList = new IntArrayList();
                            newCollisionMap[idA] = newList;
                        }
                        newList.add(idB);
                    }
                }
            }
        }

        // 3. 将全局引用指向新的数组
        collisionMap = newCollisionMap;
    }

    public static void clear() {
        Arrays.fill(collisionMap, null);
    }

    public static List<Entity> getCollisionList(Entity source, Level level) {
        int id = TempID.getId(source);
        if(id == -1 || id > collisionMap.length) {
            return Collections.emptyList();
        }
        IntArrayList ids = collisionMap[id];
//        IntArrayList ids = collisionMap.get(source.getId());
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return new EntityListView(ids, level, source);
    }

    private static class EntityListView extends AbstractList<Entity> {
        private final IntArrayList ids;
        private final Level level;
        private final Entity source;

        public EntityListView(IntArrayList ids, Level level, Entity source) {
            this.ids = ids;
            this.level = level;
            this.source = source;
        }

        @Override
        public Entity get(int index) {
            int entityId = ids.getInt(index);
//            Entity target = level.getEntity(entityId);
            Entity target = TempID.getEntity(entityId);
            if(target == null) return source;
            return target;
        }

        @Override
        public int size() {
            return ids.size();
        }

        @Override
        public boolean isEmpty() {
            return ids.isEmpty();
        }
    }
}