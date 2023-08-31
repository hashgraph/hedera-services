package com.swirlds.platform.consensus;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.MinGenInfo;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

public abstract class SyntheticSnapshot {
    public static ConsensusSnapshot generateSyntheticSnapshot(
            final long round,
            final long lastConsensusOrder,
            final Instant roundTimestamp,
            final ConsensusConfig config,
            final GossipEvent judge) {
        final List<MinGenInfo> minGenInfos = LongStream.range(
                        RoundCalculationUtils.getOldestNonAncientRound(
                                config.roundsNonAncient(), round
                        ),
                        round + 1)
                .mapToObj(r -> new MinGenInfo(r, judge.getGeneration()))
                .toList();
        return new ConsensusSnapshot(
                        round,
                        List.of(judge.getHashedData().getHash()),
                        minGenInfos,
                        lastConsensusOrder + 1,
                        ConsensusUtils.calcMinTimestampForNextEvent(roundTimestamp)
        );
    }
}
