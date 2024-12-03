/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.spec.utilops.tss;

import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater.scaleStakeToWeight;
import static com.hedera.node.app.tss.handlers.TssUtils.computeNodeShares;
import static com.hedera.node.app.tss.handlers.TssUtils.computeSharesFromWeights;
import static com.hedera.node.app.tss.handlers.TssUtils.getThresholdForTssMessages;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.exceptNodeIds;
import static com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary.FAKE_LEDGER_ID;
import static com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary.FAKE_SIGNATURE;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_FIRST_NODE_ACCOUNT_NUM;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeDelete;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.mutateStakingInfos;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.mutateTssMessages;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.mutateTssVotes;
import static com.hedera.services.bdd.spec.utilops.TssVerbs.submitTssMessage;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.suites.hip869.NodeCreateTest.generateX509Certificates;
import static com.hedera.services.bdd.suites.utils.validation.ValidationScenarios.TINYBARS_PER_HBAR;
import static com.swirlds.platform.roster.RosterRetriever.getActiveRosterHash;
import static com.swirlds.platform.roster.RosterRetriever.getCandidateRosterHash;
import static com.swirlds.platform.roster.RosterRetriever.retrieveActive;
import static java.lang.Long.parseLong;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.roster.RosterService;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssLibrary;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.spec.utilops.streams.assertions.BlockStreamAssertion;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.spi.CommittableWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * A convenience operation to test TSS roster rekeying scenarios in repeatable embedded mode. A scenario is built
 * by specifying,
 * <ol>
 *     <li>The distribution of HBAR stakes at the stake period boundary, and hence the number of shares each
 *     node should have in the candidate roster.</li>
 *     <li>The DAB edits that should be performed before the staking period change.</li>
 *     <li>The simulated message-submission behavior of the non-embedded nodes after the staking period
 *     change; where each non-embedded node will generate up to one {@link TssMessage} per share it is implied
 *     to own by the active roster in the embedded state, with that message being valid or invalid according
 *     to the {@link FakeTssLibrary} as requested.</li>
 * </ol>
 */
public class RekeyScenarioOp extends UtilOp implements BlockStreamAssertion {
    private static final Logger log = LogManager.getLogger(RekeyScenarioOp.class);
    private static final int NA = -1;
    private static final byte[] GOSSIP_CERTIFICATE;

    private record NonEmbeddedTssMessage(long nodeId, @NonNull TssMessage tssMessage) {}

    /**
     * The number of shares each node holds in the active TSS directory.
     */
    private final Map<Long, Integer> activeShares = new TreeMap<>();
    /**
     * The number of shares each node is expected to hold in the candidate TSS directory.
     */
    private final Map<Long, Integer> expectedShares = new HashMap<>();
    /**
     * The TSS messages submitted by the non-embedded nodes, in consensus order.
     */
    private final List<NonEmbeddedTssMessage> nonEmbeddedTssMessages = new ArrayList<>();

    @Nullable
    private Roster currentRoster;

    @Nullable
    private Bytes currentRosterHash;

    @Nullable
    private Bytes targetRosterHash;

    private int actualVotes = 0;
    private int actualMessages = 0;
    private int expectedVotes = NA;
    private int expectedMessages = NA;
    private int actualTssSignatures = 0;

    static {
        try {
            GOSSIP_CERTIFICATE = generateX509Certificates(1).getFirst().getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Enumerates the possible behaviors of non-embedded nodes after a staking period change.
     */
    public enum TssMessageSim {
        /**
         * The non-embedded node skips submitting TSS messages for its private shares.
         */
        SKIP_MESSAGES,
        /**
         * The non-embedded node submits valid TSS messages for its private shares.
         */
        VALID_MESSAGES,
        /**
         * The non-embedded node submits invalid TSS messages for its private shares.
         */
        INVALID_MESSAGES
    }

    /**
     * A record that encapsulates the DAB edits to be performed before the staking period change.
     *
     * @param nodesToDelete    the set of node IDs to delete
     * @param numNodesToCreate the number of nodes to create
     */
    public record DabEdits(@NonNull Set<Long> nodesToDelete, int numNodesToCreate) {
        public static DabEdits NO_DAB_EDITS = new DabEdits(Set.of(), 0);

        public DabEdits {
            requireNonNull(nodesToDelete);
        }
    }

    /**
     * A stake distribution where all nodes have equal stakes.
     */
    public static final LongUnaryOperator UNEQUAL_NODE_STAKES =
            nodeId -> (nodeId + 1) * 1_000_000_000L * TINYBARS_PER_HBAR;

    public static final LongUnaryOperator NODE_ZERO_DOMINANT_STAKES = nodeId -> switch ((int) nodeId) {
        case 0 -> 10_000_000_000L * TINYBARS_PER_HBAR;
        case 1, 2 -> 1_000_000_000L * TINYBARS_PER_HBAR;
        default -> 0;
    };

    /**
     * Factory for TSS message simulations that returns the given behaviors for each non-embedded node.
     */
    public static final Function<List<TssMessageSim>, LongFunction<TssMessageSim>> TSS_MESSAGE_SIMS =
            sims -> nodeId -> sims.get((int) ((nodeId - 1) % sims.size()));

    private final DabEdits dabEdits;
    private final LongUnaryOperator nodeStakes;
    private final LongFunction<TssMessageSim> tssMessageSims;
    private final BlockSigningType blockSigningType;

    /**
     * Constructs a {@link RekeyScenarioOp} with the given stake distribution, DAB edits, and TSS message submission
     * behaviors.
     *
     * @param dabEdits         the DAB edits
     * @param nodeStakes       the stake distribution
     * @param tssMessageSims   the TSS message submission behaviors
     * @param blockSigningType the block signing type
     */
    public RekeyScenarioOp(
            @NonNull final DabEdits dabEdits,
            @NonNull final LongUnaryOperator nodeStakes,
            @NonNull final LongFunction<TssMessageSim> tssMessageSims,
            @NonNull final BlockSigningType blockSigningType) {
        this.dabEdits = requireNonNull(dabEdits);
        this.nodeStakes = requireNonNull(nodeStakes);
        this.tssMessageSims = requireNonNull(tssMessageSims);
        this.blockSigningType = requireNonNull(blockSigningType);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        requireNonNull(spec);
        allRunFor(
                spec,
                Stream.of(
                                setupScenarioMocks(),
                                enableTss(),
                                submitDabOps(),
                                crossStakePeriodBoundary(),
                                simulateOtherNodeTssMessages())
                        .flatMap(Function.identity())
                        .toList());
        return false;
    }

    @Override
    public boolean test(@NonNull final Block block) throws AssertionError {
        final var blockNo = block.items().getFirst().blockHeaderOrThrow().number();
        log.info(
                "Pre-block#{}, votes={}/{}, messages={}/{}",
                blockNo,
                actualVotes,
                (expectedVotes == NA) ? "?" : expectedVotes,
                actualMessages,
                (expectedMessages == NA) ? "?" : expectedMessages);
        observeInteractionsIn(block);
        log.info(
                "Post-block#{}, votes={}/{}, messages={}/{}, tssSignatures={}",
                blockNo,
                actualVotes,
                (expectedVotes == NA) ? "?" : expectedVotes,
                actualMessages,
                (expectedMessages == NA) ? "?" : expectedMessages,
                actualTssSignatures);
        boolean votesAndMessagesMatch = true;
        if (expectedMessages != NA && expectedVotes != NA) {
            if (actualMessages > expectedMessages) {
                Assertions.fail(
                        "Too many messages submitted, expected " + expectedMessages + " but got " + actualMessages);
            }
            if (actualVotes > expectedVotes) {
                Assertions.fail("Too many votes submitted, expected " + expectedVotes + " but got " + actualVotes);
            }
            votesAndMessagesMatch = (actualMessages == expectedMessages) && (actualVotes == expectedVotes);
        }
        boolean signaturesMatch =
                switch (blockSigningType) {
                    case SIGN_WITH_FAKE -> actualTssSignatures == 0;
                    case SIGN_WITH_LEDGER_ID -> actualTssSignatures > 0;
                };
        return votesAndMessagesMatch && signaturesMatch;
    }

    private void observeInteractionsIn(@NonNull final Block block) {
        for (final var item : block.items()) {
            if (item.hasEventTransaction()) {
                try {
                    final var wrapper = Transaction.PROTOBUF.parse(
                            item.eventTransactionOrThrow().applicationTransactionOrThrow());
                    final var signedTxn = SignedTransaction.PROTOBUF.parse(wrapper.signedTransactionBytes());
                    final var body = TransactionBody.PROTOBUF.parse(signedTxn.bodyBytes());
                    if (body.nodeAccountIDOrElse(AccountID.DEFAULT).accountNumOrElse(0L)
                            == CLASSIC_FIRST_NODE_ACCOUNT_NUM) {
                        if (body.hasTssMessage()) {
                            actualMessages++;
                        } else if (body.hasTssVote()) {
                            actualVotes++;
                        }
                    }
                } catch (ParseException e) {
                    Assertions.fail(e.getMessage());
                }
            }
        }
        final var blockProof = block.items().getLast().blockProofOrThrow();
        if (blockProof.blockSignature().equals(Bytes.wrap(FAKE_SIGNATURE.toBytes()))) {
            actualTssSignatures++;
        }
    }

    /**
     * Returns a stream of operations that enable TSS feature flags.
     */
    private Stream<SpecOperation> enableTss() {
        if (blockSigningType == BlockSigningType.SIGN_WITH_LEDGER_ID) {
            return Stream.of(
                    overriding("tss.keyCandidateRoster", "true"), overriding("tss.signWithLedgerId", "true")
                    //                    overridingTwo("tss.signWithLedgerId", "true",
                    //                            "tss.maxSharesPerNode", "4")
                    );
        } else {
            return Stream.of(overriding("tss.keyCandidateRoster", "true"));
        }
    }

    /**
     * Returns a stream of operations that perform the requested DAB edits before the stake period boundary is crossed.
     */
    private Stream<SpecOperation> submitDabOps() {
        final List<SpecOperation> ops = new ArrayList<>();
        for (int i = 0, n = dabEdits.numNodesToCreate(); i < n; i++) {
            ops.add(newKeyNamed("adminKey" + i));
            ops.add(nodeCreate("newNode" + i).adminKey("adminKey" + i).gossipCaCertificate(GOSSIP_CERTIFICATE));
        }
        dabEdits.nodesToDelete.forEach(nodeId -> ops.add(nodeDelete("" + nodeId)));
        return ops.stream();
    }

    /**
     * Returns a stream of operations that performs the embedded state mutations and mock expectations to
     * enact the desired stake period boundary crossing scenario. In particular, this requires:
     * <ol>
     *     <li>Setting the node hbar stakes in state to the desired distribution (which determines the weights
     *     used in the participant directory derived from the candidate roster).</li>
     *     <li>Setting a well-known collection of TSS messages in state for the active roster hash.</li>
     *     <li>Preparing the {@link FakeTssLibrary} to certify these messages as valid.</li>
     *     <li>Preparing the {@link FakeTssLibrary} to decrypt the desired number of private shares
     *     for the embedded node, when it receives exactly the well-known {@link TssMessage}s.</li>
     *     <li>Preparing the {@link FakeTssLibrary} to return a well-known {@link TssMessage} for each
     *     mock private share, <i>assuming</i> the candidate participant directory has the expected
     *     node weights implied by the stake distribution at the start of the new period.</li>
     * </ol>
     */
    private Stream<SpecOperation> setupScenarioMocks() {
        return Stream.of(extractRosterMetadata(), setActiveRosterKeyMaterial(), setRequestedStakes());
    }

    /**
     * Returns a stream of operations that cross the stake period boundary, triggering the rekeying process.
     */
    private Stream<SpecOperation> crossStakePeriodBoundary() {
        return Stream.of(
                doWithStartupConfig(
                        "staking.periodMins",
                        stakePeriodMins -> waitUntilStartOfNextStakingPeriod(parseLong(stakePeriodMins))),
                // This transaction is now first in a new staking period and should trigger the TSS rekeying process,
                // in particular a successful TssMessage from the embedded node (and potentially a TssVote if the
                // non-embedded nodes submit sufficient TssMessages).
                cryptoCreate("rekeyingTransaction"));
    }

    /**
     * Returns a stream of operations that simulate the desired TSS message submission behavior of the
     * non-embedded nodes.
     */
    private Stream<SpecOperation> simulateOtherNodeTssMessages() {
        return Stream.of(doingContextual(spec -> {
            final var nextShareId = new AtomicInteger(activeShares.get(0L));
            final var numValidMessages = new AtomicInteger(expectedMessages);
            spec.targetNetworkOrThrow().nodesFor(exceptNodeIds(0L)).stream()
                    .map(HederaNode::getNodeId)
                    .forEach(nodeId -> {
                        final var sim = tssMessageSims.apply(nodeId);
                        switch (sim) {
                            case SKIP_MESSAGES -> nextShareId.getAndAdd(activeShares.get(nodeId));
                            case VALID_MESSAGES, INVALID_MESSAGES -> {
                                for (int i = 0, n = activeShares.get(nodeId); i < n; i++) {
                                    final var j = nextShareId.getAndIncrement();
                                    nonEmbeddedTssMessages.add(new NonEmbeddedTssMessage(
                                            nodeId,
                                            sim == TssMessageSim.VALID_MESSAGES
                                                    ? FakeTssLibrary.validMessage(j)
                                                    : FakeTssLibrary.invalidMessage(j)));
                                }
                                numValidMessages.getAndAdd(
                                        (sim == TssMessageSim.VALID_MESSAGES) ? activeShares.get(nodeId) : 0);
                            }
                        }
                    });
            Collections.shuffle(nonEmbeddedTssMessages);
            requireNonNull(currentRosterHash);
            targetRosterHash = getCandidateRosterHash(spec.embeddedStateOrThrow());
            final var totalShares =
                    activeShares.values().stream().mapToInt(Integer::intValue).sum();
            final var thresholdShares = getThresholdForTssMessages(totalShares);
            expectedVotes = numValidMessages.get() >= thresholdShares ? 1 : 0;
            allRunFor(
                    spec,
                    nonEmbeddedTssMessages.stream()
                            .map(m -> submitTssMessage(m.nodeId(), currentRosterHash, targetRosterHash, m.tssMessage()))
                            .toArray(SpecOperation[]::new));
        }));
    }

    /**
     * Returns an operation that extracts roster metadata from the embedded state to be used in the scenario.
     */
    private SpecOperation extractRosterMetadata() {
        return doingContextual(spec -> {
            final var state = spec.embeddedStateOrThrow();
            final var hedera = spec.repeatableEmbeddedHederaOrThrow();
            final var roundNo = hedera.lastRoundNo();

            final var writableStates = state.getWritableStates(RosterService.NAME);
            final var rosterStore = new WritableRosterStore(writableStates);
            final var activeEntries =
                    new ArrayList<>(rosterStore.getActiveRoster().rosterEntries());
            activeEntries.set(
                    activeEntries.size() - 1,
                    activeEntries.getLast().copyBuilder().weight(0).build());
            activeEntries.set(
                    0, activeEntries.getFirst().copyBuilder().weight(10).build());
            rosterStore.putActiveRoster(new Roster(activeEntries), roundNo + 1);
            ((CommittableWritableStates) writableStates).commit();

            // Extract all the roster metadata for the active roster
            currentRoster = requireNonNull(retrieveActive(state, roundNo));
            currentRosterHash = getActiveRosterHash(state, roundNo);
            computeNodeShares(
                            currentRoster.rosterEntries(),
                            spec.startupProperties().getInteger("tss.maxSharesPerNode"))
                    .forEach((nodeId, numShares) -> activeShares.put(nodeId, numShares.intValue()));
            expectedMessages = activeShares.get(0L);
            // Prepare the FakeTssLibrary to decrypt the private shares of the embedded node
            hedera.tssBaseService()
                    .fakeTssLibrary()
                    .setupDecryption(
                            directory -> {},
                            IntStream.range(0, activeShares.get(0L)).boxed().toList());
        });
    }

    /**
     * Returns an operation that sets the requested node stakes in state for the given spec. These stakes
     * should define the weights used in the participant directory derived from the candidate roster.
     */
    private SpecOperation setRequestedStakes() {
        return sourcingContextual(spec -> mutateStakingInfos(infos -> {
            final Map<Long, Long> stakes = new HashMap<>();
            spec.targetNetworkOrThrow().nodes().forEach(node -> {
                final var key = new EntityNumber(node.getNodeId());
                final var info = requireNonNull(infos.get(key));
                final var stake = nodeStakes.applyAsLong(node.getNodeId());
                infos.put(
                        key,
                        info.copyBuilder().stake(stake).stakeToReward(stake).build());
                stakes.put(node.getNodeId(), stake);
                log.info("Set stake for node {} to {} tinybars", node.getNodeId(), stake);
            });
            final var totalWeight = spec.startupProperties().getInteger("staking.sumOfConsensusWeights");
            final var totalStake =
                    stakes.values().stream().mapToLong(Long::longValue).sum();
            final var weights = stakes.keySet().stream().collect(Collectors.toMap(Function.identity(), nodeId ->
                    (long) scaleStakeToWeight(stakes.get(nodeId), totalStake, totalWeight)));
            final var maxShares = spec.startupProperties().getInteger("tss.maxSharesPerNode");
            computeSharesFromWeights(weights, maxShares)
                    .forEach((nodeId, numShares) -> expectedShares.put(nodeId, numShares.intValue()));
            spec.repeatableEmbeddedHederaOrThrow()
                    .tssBaseService()
                    .fakeTssLibrary()
                    .setupRekeyGeneration(directory -> {
                        expectedShares.forEach((nodeId, expectedNumShares) -> assertEquals(
                                expectedNumShares,
                                directory.ownedShares(nodeId).size(),
                                "Wrong number of shares for node" + nodeId));
                    });
        }));
    }

    /**
     * Returns an operation that sets the key material for the active roster in the embedded state.
     * This has two components.
     * <ol>
     *     <li>First, the synthetic TSS messages whose keys specify the active roster hash.</li>
     *     <li>Second, the synthetic TSS votes whose keys specify the source roster hash (which
     *     is empty, since the current roster is in the first active roster in this scenario) and
     *     whose values specify the active roster hash; and concur on the consensus subset of TSS
     *     messages used to form the TSS keys for the active roster (which for convenience we take
     *     in order of share index).</li>
     * </ol>
     */
    private SpecOperation setActiveRosterKeyMaterial() {
        final var nextShareId = new AtomicInteger(0);
        final var voteSet = new BitSet();
        return blockingOrder(
                mutateTssMessages(tssMessages -> {
                    requireNonNull(currentRosterHash);
                    final var totalShares = activeShares.values().stream()
                            .mapToInt(Integer::intValue)
                            .sum();
                    final var thresholdShares = getThresholdForTssMessages(totalShares);
                    activeShares.forEach((nodeId, numShares) -> {
                        for (int i = 0; i < numShares; i++) {
                            final var shareId = nextShareId.getAndIncrement();
                            final var key = new TssMessageMapKey(currentRosterHash, shareId);
                            final var tssMessage = Bytes.wrap(FakeTssLibrary.validMessage((int) key.sequenceNumber())
                                    .toBytes());
                            final var value = TssMessageTransactionBody.newBuilder()
                                    .sourceRosterHash(Bytes.EMPTY)
                                    .targetRosterHash(currentRosterHash)
                                    .shareIndex(key.sequenceNumber())
                                    .tssMessage(tssMessage)
                                    .build();
                            tssMessages.put(key, value);
                            if (voteSet.cardinality() < thresholdShares) {
                                voteSet.set(shareId);
                            }
                        }
                    });
                }),
                mutateTssVotes(tssVotes -> {
                    final var tssVote = Bytes.wrap(voteSet.toByteArray());
                    requireNonNull(currentRosterHash);
                    requireNonNull(currentRoster).rosterEntries().forEach(entry -> {
                        final var key = new TssVoteMapKey(currentRosterHash, entry.nodeId());
                        final var vote = TssVoteTransactionBody.newBuilder()
                                .tssVote(tssVote)
                                .sourceRosterHash(Bytes.EMPTY)
                                .targetRosterHash(currentRosterHash)
                                .ledgerId(Bytes.wrap(FAKE_LEDGER_ID.toBytes()))
                                .build();
                        tssVotes.put(key, vote);
                    });
                }));
    }

    public enum BlockSigningType {
        SIGN_WITH_LEDGER_ID,
        SIGN_WITH_FAKE
    }
}
