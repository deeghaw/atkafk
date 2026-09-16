package com.automine.AutoMineMod.navigation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Proportional camera steering with human-like micro-tremors and inertia.
 * Features:
 * - Natural hand tremor simulation
 * - Variable rotation speed based on context
 * - Inertia when changing direction
 * - Deadzone snap for jitter elimination
 */
public class CameraSteering {
    private static final float MAX_YAW_SPEED = 22.0f;
    private static final float MAX_PITCH_SPEED = 14.0f;
    private static final float SNAP_YAW_DEG = 0.30f;
    private static final float SNAP_PITCH_DEG = 0.20f;

    // Human-like movement parameters
    private static final float TREMOR_INTENSITY = 0.08f;
    private static final float INERTIA_FACTOR = 0.15f;
    private static final float SPEED_VARIATION_RANGE = 0.12f;

    private float yaw;
    private float pitch;
    private boolean initialized;

    // For inertia calculation
    private float lastYawStep = 0;
    private float lastPitchStep = 0;

    // Tick counter for tremor
    private int tickCount = 0;

    public void reset(float initialYaw, float initialPitch) {
        yaw = Mth.wrapDegrees(initialYaw);
        pitch = Mth.clamp(initialPitch, -89.9f, 89.9f);
        initialized = true;
        lastYawStep = 0;
        lastPitchStep = 0;
        tickCount = 0;
    }

    public void invalidate() {
        initialized = false;
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void steer(float targetYaw, float targetPitch, float responsiveness) {
        steer(targetYaw, targetPitch, responsiveness, MAX_YAW_SPEED, MAX_PITCH_SPEED);
    }

    public void steer(float targetYaw, float targetPitch, float responsiveness,
                      float maxYawSpeed, float maxPitchSpeed) {
        if (!initialized) {
            reset(targetYaw, targetPitch);
            return;
        }

        tickCount++;

        targetYaw = Mth.wrapDegrees(targetYaw);
        targetPitch = Mth.clamp(targetPitch, -75.0f, 75.0f);

        float yawError = Mth.degreesDifference(yaw, targetYaw);
        float pitchError = targetPitch - pitch;

        float speedMultiplier = 1.0f + (float)(Math.sin(tickCount * 0.1) * SPEED_VARIATION_RANGE);
        float adjustedMaxYaw = maxYawSpeed * speedMultiplier;
        float adjustedMaxPitch = maxPitchSpeed * speedMultiplier;

        float rawYawStep = Mth.clamp(yawError * responsiveness, -adjustedMaxYaw, adjustedMaxYaw);
        float rawPitchStep = Mth.clamp(pitchError * responsiveness, -adjustedMaxPitch, adjustedMaxPitch);

        float yawStep = rawYawStep + lastYawStep * INERTIA_FACTOR;
        float pitchStep = rawPitchStep + lastPitchStep * INERTIA_FACTOR;

        lastYawStep = yawStep;
        lastPitchStep = pitchStep;

        if (Math.abs(yawError) <= SNAP_YAW_DEG) yawStep = yawError;
        if (Math.abs(pitchError) <= SNAP_PITCH_DEG) pitchStep = pitchError;

        yaw = Mth.wrapDegrees(yaw + yawStep);
        pitch = Mth.clamp(pitch + pitchStep, -89.9f, 89.9f);
    }

    /**
     * Apply micro-tremor to camera for natural hand movement.
     * Call this after main steering to add subtle jitter.
     */
    public void applyMicroTremor(float intensity) {
        if (!initialized || intensity <= 0) return;

        Vec3 tremor = HumanMovement.getCameraTremor(tickCount, intensity * TREMOR_INTENSITY);
        yaw = Mth.wrapDegrees(yaw + (float)tremor.x);
        pitch = Mth.clamp(pitch + (float)tremor.y, -89.9f, 89.9f);
    }
}
