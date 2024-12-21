package com.hedera.node.app.hints.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.hints.HintsService;
import com.swirlds.state.lifecycle.Schema;

/**
 * Registers the states needed for the {@link HintsService}; these are,
 * <ul>
 *     <li>A singleton with the number of constructions attempted.</li>
 *     <li>A singleton with the active hinTS construction (must be at least
 *     ongoing once the network is active; and until complete, the hinTS service
 *     will not be able to aggregate partial signatures).</li>
 *     <li>A singleton with the next hinTS construction (may or may not be
 *     ongoing, as there may not be a candidate roster set).</li>
 *     <li>A map from node id to the node's timestamped active hinTS key; and,
 *     if applicable, the hinTS key it wants to start using for all constructions
 *     beginning after the current stake period.</li>
 *     <li>A map from pair of node id and construction id to the node's
 *     vote for the keys output from preprocessing in that construction.</li>
 * </ul>
 */
public class V058HintsSchema extends Schema {
    private static final long MAX_HINTS = 65_536L;
    private static final long MAX_PREPROCESSING_VOTES = 2 * MAX_HINTS;

    private static final SemanticVersion VERSION = SemanticVersion.newBuilder().minor(58).build();

    public static final String CONSTRUCTION_COUNT_KEY = "CONSTRUCTION_COUNT";
    public static final String ACTIVE_CONSTRUCTION_KEY = "ACTIVE_CONSTRUCTION";
    public static final String PREPROCESSING_VOTES_KEY = "PREPROCESSING_VOTES";
    public static final String NEXT_CONSTRUCTION_KEY = "NEXT_CONSTRUCTION";
    public static final String HINTS_KEY = "HINTS";

    public V058HintsSchema() {
        super(VERSION);
    }
}
