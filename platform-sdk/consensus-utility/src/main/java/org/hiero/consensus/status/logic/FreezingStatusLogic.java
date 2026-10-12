// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.status.logic;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hiero.consensus.model.status.ConsensusLayerStatus;
import org.hiero.consensus.status.actions.FallenBehindAction;

/**
 * Class containing the state machine logic for the {@link ConsensusLayerStatus#FREEZING} status.
 */
public class FreezingStatusLogic extends AbstractStatusLogic {
    /**
     * The round number when the freeze started
     */
    private final long freezeRound;

    /**
     * Constructor
     *
     * @param freezeRound the round number when the freeze started
     */
    public FreezingStatusLogic(final long freezeRound) {
        super(ConsensusLayerStatus.FREEZING);
        this.freezeRound = freezeRound;
    }

    /**
     * Receiving a {@link FallenBehindAction} while in {@link ConsensusLayerStatus#FREEZING} has no effect on the state
     * machine.
     */
    @NonNull
    @Override
    protected PlatformStatusLogic onFallenBehind(@NonNull final FallenBehindAction action) {
        return this;
    }
}
