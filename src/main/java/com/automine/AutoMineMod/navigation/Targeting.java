package com.automine.AutoMineMod.navigation;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Targeting {
    public static final double TARGET_RADIUS_HORIZONTAL = 48.0;
    public static final double TARGET_RANGE_VERTICAL = 6.0;
    public static final double CREEPER_FLEE_RADIUS = 10.0;
    public static final double CREEPER_SAFE_DISTANCE = 8.0;
    public static final double CREEPER_TARGET_PENALTY = 100000.0;

    private Targeting() {
    }

    public static boolean isAquatic(LivingEntity entity) {
        if (entity.isInWater()) return true;
        return entity instanceof WaterAnimal
                || entity instanceof Axolotl
                || entity instanceof Squid
                || entity instanceof GlowSquid
                || entity instanceof Dolphin
                || entity instanceof Turtle
                || entity instanceof Guardian
                || entity instanceof ElderGuardian
                || entity instanceof Drowned;
    }

    public static boolean isCreeper(LivingEntity entity) {
        return entity instanceof Creeper;
    }

    public static AABB targetSearchBox(LivingEntity viewer) {
        Vec3 pos = viewer.position();
        return new AABB(
                pos.x - TARGET_RADIUS_HORIZONTAL,
                pos.y - TARGET_RANGE_VERTICAL,
                pos.z - TARGET_RADIUS_HORIZONTAL,
                pos.x + TARGET_RADIUS_HORIZONTAL,
                pos.y + TARGET_RANGE_VERTICAL,
                pos.z + TARGET_RADIUS_HORIZONTAL
        );
    }

    public static boolean withinTargetRange(LivingEntity viewer, LivingEntity target) {
        double dx = target.getX() - viewer.getX();
        double dz = target.getZ() - viewer.getZ();
        return dx * dx + dz * dz <= TARGET_RADIUS_HORIZONTAL * TARGET_RADIUS_HORIZONTAL
                && Math.abs(target.getY() - viewer.getY()) <= TARGET_RANGE_VERTICAL;
    }

    public static Creeper findNearestCreeper(LivingEntity viewer, double radius) {
        Creeper nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity e : viewer.level().getEntitiesOfClass(LivingEntity.class,
                viewer.getBoundingBox().inflate(radius),
                e -> e != viewer && e.isAlive() && e instanceof Creeper)) {
            double d = viewer.distanceToSqr(e);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = (Creeper) e;
            }
        }
        return nearest;
    }
}
