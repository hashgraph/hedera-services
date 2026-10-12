// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hiero.consensus.model.status.ConsensusLayerStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class HederaGossipAvailabilityTest {
    @ParameterizedTest
    @EnumSource(
            value = ConsensusLayerStatus.class,
            names = {"ACTIVE", "CHECKING", "FREEZING"})
    void gossipAvailableForStatusesThatCanCreatePriorityEvents(final ConsensusLayerStatus status) {
        assertTrue(Hedera.isGossipAvailableForNodeTransactions(status));
    }

    @ParameterizedTest
    @EnumSource(
            value = ConsensusLayerStatus.class,
            names = {"ACTIVE", "CHECKING", "FREEZING"},
            mode = EnumSource.Mode.EXCLUDE)
    void gossipUnavailableForStatusesThatCannotCreatePriorityEvents(final ConsensusLayerStatus status) {
        assertFalse(Hedera.isGossipAvailableForNodeTransactions(status));
    }
}
