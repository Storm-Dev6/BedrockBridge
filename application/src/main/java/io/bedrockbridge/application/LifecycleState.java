package io.bedrockbridge.application;

/** Complete states of the standalone bridge process lifecycle. */
public enum LifecycleState {
    NEW,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
