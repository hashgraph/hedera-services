package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * The data received during the second phase of a sync.
 *
 * @param theirBooleans    For each tip we send to the peer, they respond with a single boolean saying "yes I have this
 *                         event" or "no I don't have this event". This order of those booleans corresponds to the order
 *                         of the tips we sent to the peer.
 * @param theirUpdatedTips During this phase of the sync, if configured to do so the peer will send an updated list of
 *                         tips. This helps to reduce duplicate events in scenarios where there is a lot of latency
 *                         between each phase in a sync.
 */
public record TheirBooleansAndUpdatedTips(@NonNull List<Boolean> theirBooleans, @Nullable List<Hash> theirUpdatedTips) {
}
