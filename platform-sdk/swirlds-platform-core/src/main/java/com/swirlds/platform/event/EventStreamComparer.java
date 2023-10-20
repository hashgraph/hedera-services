package com.swirlds.platform.event;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.platform.recovery.internal.EventStreamLowerBound.UNBOUNDED;
import static com.swirlds.platform.util.BootstrapUtils.setupConstructableRegistry;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.platform.recovery.internal.EventStreamLowerBound;
import com.swirlds.platform.recovery.internal.EventStreamMultiFileIterator;
import com.swirlds.platform.recovery.internal.EventStreamRoundLowerBound;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class EventStreamComparer {

    private static final Path streamA = getAbsolutePath("~/Downloads/eventsStreams0");
    private static final Path streamB = getAbsolutePath("~/Downloads/eventsStreams0");

    private static final long issRound = 199232;
    private static final long firstRoundToScan = issRound - 10;


    public static void compare() throws IOException, ConstructableRegistryException {

        setupConstructableRegistry();
        ConstructableRegistry.getInstance().registerConstructables("com.hedera");
//        ConstructableRegistry.getInstance()
//                .registerConstructable(new ClassConstructorPair(SerializableSemVers.class,
//                        SelfHashingDummyMerkleLeaf::new));

        final EventStreamRoundLowerBound bound = new EventStreamRoundLowerBound(firstRoundToScan);

        final IOIterator<DetailedConsensusEvent> iteratorA = new EventStreamMultiFileIterator(streamA, UNBOUNDED);
        final IOIterator<DetailedConsensusEvent> iteratorB = new EventStreamMultiFileIterator(streamB, UNBOUNDED);

        int maxDiff = 3;
        int eventsInRoundBeforeIss = 0;
        int evnentsInIssRound = 0;
        int eventsInRoundAfterIss = 0;

        final DetailedConsensusEvent firstEventA = iteratorA.peek();
        System.out.println("First event created = " + firstEventA.getConsensusData().getRoundCreated());
        System.out.println("First event round = " + firstEventA.getConsensusData().getRoundReceived());

        DetailedConsensusEvent lastEvent = null;

        while (iteratorA.hasNext() && iteratorB.hasNext()) {

            final DetailedConsensusEvent a = iteratorA.next();
            final DetailedConsensusEvent b = iteratorB.next();
            lastEvent = a;

            if (a.getConsensusData().getRoundReceived() == issRound - 1) {
                eventsInRoundAfterIss++;
            } else if (a.getConsensusData().getRoundReceived() == issRound) {
                evnentsInIssRound++;
            } else if (a.getConsensusData().getRoundReceived() == issRound + 1) {
                eventsInRoundAfterIss++;
            }

            if (!a.equals(b) && maxDiff-- > 0) {
                final boolean hashedDataIsDifferent = !a.getBaseEventHashedData().equals(b.getBaseEventHashedData());
                final boolean unhashedDataIsDifferent = !a.getBaseEventUnhashedData()
                        .equals(b.getBaseEventUnhashedData());
                final boolean consensusDataIsDifferent = !a.getConsensusData().equals(b.getConsensusData());

                System.out.println("-------------- Divergent event detected --------------");
                System.out.println("Hashed data is different: " + hashedDataIsDifferent);
                System.out.println("Unhashed data is different: " + unhashedDataIsDifferent);
                System.out.println("Consensus data is different: " + consensusDataIsDifferent);

                System.out.println("***");

                final BaseEventHashedData hashedDataA = a.getBaseEventHashedData();
                final BaseEventHashedData hashedDataB = b.getBaseEventHashedData();

                final boolean softwareVersionIsDifferent =
                        hashedDataA.getSoftwareVersion().compareTo(hashedDataB.getSoftwareVersion()) != 0;
                final boolean creatorIdIsDifferent = !hashedDataA.getCreatorId().equals(hashedDataB.getCreatorId());
                final boolean selfParentGenIsDifferent =
                        hashedDataA.getSelfParentGen() != hashedDataB.getSelfParentGen();
                final boolean otherParentGenIsDifferent =
                        hashedDataA.getOtherParentGen() != hashedDataB.getOtherParentGen();
                final boolean selfParentHashIsDifferent = !hashedDataA.getSelfParentHash()
                        .equals(hashedDataB.getSelfParentHash());
                final boolean otherParentHashIsDifferent = !hashedDataA.getOtherParentHash()
                        .equals(hashedDataB.getOtherParentHash());
                final boolean timeCreatedIsDifferent = !hashedDataA.getTimeCreated()
                        .equals(hashedDataB.getTimeCreated());
                final boolean differentTransactions = !Arrays.equals(hashedDataA.getTransactions(),
                        hashedDataB.getTransactions());

                System.out.println("Software version is different: " + softwareVersionIsDifferent);
                System.out.println("Creator ID is different: " + creatorIdIsDifferent);
                System.out.println("Self parent gen is different: " + selfParentGenIsDifferent);
                System.out.println("Other parent gen is different: " + otherParentGenIsDifferent);
                System.out.println("Self parent hash is different: " + selfParentHashIsDifferent);
                System.out.println("Other parent hash is different: " + otherParentHashIsDifferent);
                System.out.println("Time created is different: " + timeCreatedIsDifferent);
                System.out.println("Transactions are different: " + differentTransactions);

                System.out.println("Event A: " + a);
                System.out.println("Event B: " + b);
            }
        }

        System.out.println("Events in round before ISS: " + eventsInRoundBeforeIss);
        System.out.println("Events in ISS round: " + evnentsInIssRound);
        System.out.println("Events in round after ISS: " + eventsInRoundAfterIss);

        System.out.println("Lat event round = " + lastEvent.getConsensusData().getRoundReceived());

    }

}
