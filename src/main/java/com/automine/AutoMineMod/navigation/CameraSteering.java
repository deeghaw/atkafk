package com.automine.AutoMineMod.navigation;

import net.minecraft.util.Mth;

/**
 * Proportional camera steering with a per-tick angular cap.
 * Fast convergence for large errors, smooth micro-adjustments for small ones,
 * and an exact snap inside a tiny deadzone to kill residual jitter.
 */
public class CameraSteering {
    private static final float MAX_YAW_SPEED = 30.0f;
    private static final float MAX_PITCH_SPEED = 18.0f;
    private static final float SNAP_YAW_DEG = 0.30f;
    private static final float SNAP_PITCH_DEG = 0.20f;

    private float yaw;
    private float pitch;
    private boolean initialized;

    public void reset(float initialYaw, float initialPitch) {
        yaw = Mth.wrapDegrees(initialYaw);
        pitch = Mth.clamp(initialPitch, -89.9f, 89.9f);
        initialized = true;
    }

    public void invalidate() {
        initialized = false;
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void steer(float targetYaw, float targetPitch, float responsiveness) {
        if (!initialized) {
            reset(targetYaw, targetPitch);
            return;
        }

        targetYaw = Mth.wrapDegrees(targetYaw);
        targetPitch = Mth.clamp(targetPitch, -75.0f, 75.0f);

        float yawError = Mth.degreesDifference(yaw, targetYaw);
        float pitchError = targetPitch - pitch;

        float yawStep = Mth.clamp(yawError * responsiveness, -MAX_YAW_SPEED, MAX_YAW_SPEED);
        float pitchStep = Mth.clamp(pitchError * responsiveness, -MAX_PITCH_SPEED, MAX_PITCH_SPEED);

        if (Math.abs(yawError) <= SNAP_YAW_DEG) yawStep = yawError;
        if (Math.abs(pitchError) <= SNAP_PITCH_DEG) pitchStep = pitchError;

        yaw = Mth.wrapDegrees(yaw + yawStep);
        pitch = Mth.clamp(pitch + pitchStep, -89.9f, 89.9f);
    }
}
