// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.status.actions;

import org.hiero.consensus.model.status.ConsensusLayerStatus;

/**
 * An interface for platform status actions.
 * <p>
 * A platform status action is an occurrence that has the potential to affect the
 * {@link ConsensusLayerStatus PlatformStatus}
 */
public sealed interface PlatformStatusAction
        permits CatastrophicFailureAction,
                DoneReplayingEventsAction,
                FallenBehindAction,
                FreezePeriodEnteredAction,
                SelfEventReachedConsensusAction,
                StartedReplayingEventsAction,
                FreezeCompleteAction,
                TimeElapsedAction {}
