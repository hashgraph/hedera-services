package com.swirlds.platform.consensus;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.MinGenInfo;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.LongStream;

public record SyntheticSnapshot(ConsensusSnapshot snapshot, GossipEvent judge) {
    public static SyntheticSnapshot generateSyntheticSnapshot(
            final SoftwareVersion softwareVersion,
            final NodeId creatorId,
            final long round,
            final long minGeneration,
            final long lastConsensusOrder,
            final Instant roundTimestamp,
            final ConsensusConfig config,
            final Function<SerializableHashable, Hash> hasher,
            final Signer signer) {
        final List<MinGenInfo> minGenInfos = LongStream.range(
                        RoundCalculationUtils.getOldestNonAncientRound(
                                config.roundsNonAncient(), round
                        ),
                        round + 1)
                .mapToObj(r -> new MinGenInfo(r, minGeneration))
                .toList();

        final byte[] parentHash = new byte[DigestType.SHA_384.digestLength()];
        new Random().nextBytes(parentHash);
        final BaseEventHashedData hashedData = new BaseEventHashedData(
                softwareVersion,
                creatorId,
                minGeneration - 1,
                EventConstants.GENERATION_UNDEFINED,
                new Hash(parentHash, DigestType.SHA_384),
                null,
                roundTimestamp.plusSeconds(1),
                new ConsensusTransactionImpl[0]
        );
        hasher.apply(hashedData);
        final GossipEvent judge = new GossipEvent(
                hashedData,
                new BaseEventUnhashedData(
                        EventConstants.CREATOR_ID_UNDEFINED,
                        signer.sign(hashedData.getHash().getValue()).getSignatureBytes()
                )
        );
        judge.buildDescriptor();

        return new SyntheticSnapshot(
                new ConsensusSnapshot(
                        round,
                        List.of(judge.getHashedData().getHash()),
                        minGenInfos,
                        lastConsensusOrder + 1,
                        ConsensusUtils.calcMinTimestampForNextEvent(roundTimestamp)),
                judge
        );
    }
}
