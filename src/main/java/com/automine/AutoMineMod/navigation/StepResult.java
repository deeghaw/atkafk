package com.automine.AutoMineMod.navigation;

public record StepResult(boolean walkable, boolean needJump) {
    public static final StepResult BLOCKED = new StepResult(false, false);
    public static final StepResult CLEAR = new StepResult(true, false);
    public static final StepResult STEP_UP = new StepResult(true, true);
}
