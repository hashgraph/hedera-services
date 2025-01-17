package com.hedera.services.bdd.junit.hedera;

/**
 * Defines the different modes for block node operation in tests.
 */
public enum BlockNodeMode {
    /** Use Docker containers for block nodes */
    CONTAINERS,
    
    /** Use a simulated block node */
    SIMULATOR,
    
    /** Don't use any block nodes */
    NONE
} 