package dev.peregrinegeo.peregrine.core.kinematics

/**
 * Pure Kotlin kinematic simulation engine.
 *
 * ARCHITECTURAL RULE:
 * This package must remain strictly pure.
 * Absolutely NO Android framework, network, UI, or database imports are permitted.
 * The clock is injected through a TimeSource interface.
 */
