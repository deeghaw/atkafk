package com.automine.AutoMineMod.navigation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Provides human-like movement variations including:
 * - Strafe patterns (sinusoidal lateral movement)
 * - Micro-hesitations and pauses
 * - Speed variations
 * - Look-around behavior
 * - Jump timing variations
 */
public final class HumanMovement {
    private static final Random RANDOM = new Random();

    // Strafe configuration
    private static final double STRAFE_AMPLITUDE = 0.18;
    private static final int STRAFE_PERIOD_TICKS = 80;
    private static final double COMBAT_STRAFE_AMPLITUDE = 0.25;
    private static final int COMBAT_STRAFE_PERIOD_TICKS = 60;

    // Strafe gating - only apply strafe in short bursts, not continuously
    private static final double STRAFE_BURST_CHANCE = 0.01;
    private static final int MIN_STRAFE_BURST_TICKS = 10;
    private static final int MAX_STRAFE_BURST_TICKS = 25;
    private static final int STRAFE_GAP_TICKS = 80;

    // Hesitation configuration
    private static final double HESITATION_CHANCE = 0.008;
    private static final int MIN_HESITATION_TICKS = 4;
    private static final int MAX_HESITATION_TICKS = 12;

    // Look-around configuration
    private static final double LOOK_AROUND_CHANCE = 0.005;
    private static final int MIN_LOOK_AROUND_TICKS = 8;
    private static final int MAX_LOOK_AROUND_TICKS = 20;
    private static final float LOOK_AROUND_MAX_YAW = 35.0f;

    // Jump variation
    private static final double EXTRA_JUMP_CHANCE = 0.02;
    private static final int JUMP_VARIATION_TICKS = 3;

    // Speed variation
    private static final double SPEED_VARIATION_RANGE = 0.15;

    private HumanMovement() {
    }

    /**
     * Calculate strafe offset for natural lateral movement.
     * Uses a gated burst system so strafe does not run continuously.
     * @param tickCount Current game tick for sinusoidal calculation
     * @param isCombat Whether in combat mode (faster strafe)
     * @return Strafe offset (-1.0 to 1.0) to be added to movement direction
     */
    public static double getStrafeOffset(int tickCount, boolean isCombat) {
        int period = isCombat ? COMBAT_STRAFE_PERIOD_TICKS : STRAFE_PERIOD_TICKS;
        double amplitude = isCombat ? COMBAT_STRAFE_AMPLITUDE : STRAFE_AMPLITUDE;

        double phase = (double) tickCount / period * Math.PI * 2.0;
        double baseStrafe = Math.sin(phase) * amplitude;

        return Mth.clamp(baseStrafe, -1.0, 1.0);
    }

    /**
     * Check if a new strafe burst should begin.
     * Strafe only happens in short bursts separated by gaps.
     * @return true if a new strafe burst should start
     */
    public static boolean shouldStartStrafeBurst() {
        return RANDOM.nextDouble() < STRAFE_BURST_CHANCE;
    }

    /**
     * Get duration for a strafe burst.
     * @return Number of ticks the strafe burst lasts
     */
    public static int getStrafeBurstDuration() {
        return MIN_STRAFE_BURST_TICKS + RANDOM.nextInt(MAX_STRAFE_BURST_TICKS - MIN_STRAFE_BURST_TICKS);
    }

    /**
     * Get gap duration between strafe bursts.
     * @return Number of ticks with no strafe
     */
    public static int getStrafeGapDuration() {
        return STRAFE_GAP_TICKS + RANDOM.nextInt(60);
    }

    /**
     * Calculate strafe direction keys (left/right) based on forward direction and strafe offset.
     * @param forwardDir The forward movement direction
     * @param strafeOffset Strafe offset from getStrafeOffset
     * @return Modified direction with strafe applied
     */
    public static Vec3 applyStrafe(Vec3 forwardDir, double strafeOffset) {
        if (forwardDir == null || Math.abs(strafeOffset) < 0.01) {
            return forwardDir;
        }

        Vec3 right = new Vec3(-forwardDir.z, 0, forwardDir.x).normalize();
        return forwardDir.add(right.scale(strafeOffset)).normalize();
    }

    /**
     * Determine strafe action based on current strafe status.
     * @return 1 for right, -1 for left, 0 for no strafe
     */
    public static int getStrafeAction(double strafeOffset, boolean strafeActive) {
        if (!strafeActive) return 0;
        if (strafeOffset > 0) return 1;
        if (strafeOffset < 0) return -1;
        return 0;
    }

    /**
     * Check if bot should hesitate (pause briefly).
     * @return true if hesitation should occur
     */
    public static boolean shouldHesitate() {
        return RANDOM.nextDouble() < HESITATION_CHANCE;
    }

    /**
     * Get duration for a hesitation pause.
     * @return Number of ticks to hesitate
     */
    public static int getHesitationDuration() {
        return MIN_HESITATION_TICKS + RANDOM.nextInt(MAX_HESITATION_TICKS - MIN_HESITATION_TICKS);
    }

    /**
     * Check if bot should look around (brief camera rotation).
     * @return true if look-around should occur
     */
    public static boolean shouldLookAround() {
        return RANDOM.nextDouble() < LOOK_AROUND_CHANCE;
    }

    /**
     * Get yaw offset for look-around behavior.
     * @return Yaw offset in degrees
     */
    public static float getLookAroundYaw() {
        return (RANDOM.nextFloat() - 0.5f) * 2.0f * LOOK_AROUND_MAX_YAW;
    }

    /**
     * Get duration for look-around behavior.
     * @return Number of ticks to spend looking around
     */
    public static int getLookAroundDuration() {
        return MIN_LOOK_AROUND_TICKS + RANDOM.nextInt(MAX_LOOK_AROUND_TICKS - MIN_LOOK_AROUND_TICKS);
    }

    /**
     * Check if bot should add an extra jump (not required for terrain).
     * @return true if extra jump should occur
     */
    public static boolean shouldExtraJump() {
        return RANDOM.nextDouble() < EXTRA_JUMP_CHANCE;
    }

    /**
     * Get jump timing variation.
     * @return Additional ticks to add to jump pulse
     */
    public static int getJumpVariation() {
        return RANDOM.nextInt(JUMP_VARIATION_TICKS);
    }

    /**
     * Apply natural speed variation to movement.
     * @param baseSpeed Base movement speed multiplier
     * @return Modified speed with natural variation
     */
    public static double applySpeedVariation(double baseSpeed) {
        double variation = (RANDOM.nextDouble() - 0.5) * 2.0 * SPEED_VARIATION_RANGE;
        return baseSpeed + variation;
    }

    /**
     * Calculate camera micro-tremor for natural hand movement.
     * @param tickCount Current tick
     * @param intensity Tremor intensity (0.0 - 1.0)
     * @return Yaw and pitch offsets as Vec3(x=yaw, y=pitch, z=0)
     */
    public static Vec3 getCameraTremor(int tickCount, double intensity) {
        if (intensity <= 0) return Vec3.ZERO;

        double yawTremor = Math.sin(tickCount * 0.7) * 0.15
                + Math.sin(tickCount * 1.3) * 0.1
                + (RANDOM.nextDouble() - 0.5) * 0.08;
        double pitchTremor = Math.cos(tickCount * 0.9) * 0.1
                + Math.cos(tickCount * 1.7) * 0.07
                + (RANDOM.nextDouble() - 0.5) * 0.05;

        return new Vec3(yawTremor * intensity, pitchTremor * intensity, 0);
    }

    /**
     * Get combat strafe pattern (circular movement around target).
     * @param tickCount Current tick
     * @param isAggressive Whether to move toward target while strafing
     * @return Direction vector for strafe movement
     */
    public static Vec3 getCombatStrafePattern(int tickCount, boolean isAggressive) {
        double phase = (double) tickCount / COMBAT_STRAFE_PERIOD_TICKS * Math.PI * 2.0;
        double strafe = Math.sin(phase);
        double forward = isAggressive ? Math.cos(phase) * 0.3 : 0;

        return new Vec3(strafe, 0, forward).normalize();
    }

    /**
     * Check if bot should vary attack timing (not attack exactly on cooldown).
     * @return true if attack should be delayed slightly
     */
    public static boolean shouldVaryAttackTiming() {
        return RANDOM.nextDouble() < 0.15;
    }

    /**
     * Get attack timing variation in ticks.
     * @return Additional ticks to delay attack
     */
    public static int getAttackTimingVariation() {
        return RANDOM.nextInt(2);
    }

    /**
     * Calculate exploration curiosity factor.
     * Higher values make bot more likely to investigate new areas.
     * @param distanceFromHome Distance from last "home" position
     * @return Curiosity factor (0.0 - 1.0)
     */
    public static double getExplorationCuriosity(double distanceFromHome) {
        double baseCuriosity = 0.5;
        double distanceBonus = Math.min(distanceFromHome / 50.0, 0.5);
        return Mth.clamp(baseCuriosity + distanceBonus + (RANDOM.nextDouble() - 0.5) * 0.2, 0.0, 1.0);
    }

    /**
     * Get random deviation for exploration direction.
     * @return Angle deviation in degrees
     */
    public static double getExplorationDeviation() {
        return (RANDOM.nextDouble() - 0.5) * 120.0;
    }
}
