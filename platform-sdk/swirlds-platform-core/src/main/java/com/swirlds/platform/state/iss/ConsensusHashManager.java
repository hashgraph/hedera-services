/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.iss;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.logging.LogMarker.STATE_HASH;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.sequence.map.ConcurrentSequenceMap;
import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.logging.payloads.IssPayload;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.Observer;
import com.swirlds.platform.dispatch.triggers.error.CatastrophicIssTrigger;
import com.swirlds.platform.dispatch.triggers.error.SelfIssTrigger;
import com.swirlds.platform.dispatch.triggers.flow.DiskStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.ReconnectStateLoadedTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashValidityTrigger;
import com.swirlds.platform.dispatch.triggers.flow.StateHashedTrigger;
import com.swirlds.platform.state.iss.internal.ConsensusHashFinder;
import com.swirlds.platform.state.iss.internal.HashValidityStatus;
import com.swirlds.platform.state.iss.internal.RoundHashValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps track of the state hashes reported by all network nodes. Responsible for detecting ISS events.
 */
public class ConsensusHashManager {

    private static final Logger logger = LogManager.getLogger(ConsensusHashManager.class);

    private final SequenceMap<Long /* round */, RoundHashValidator> roundData;

    private long previousRound = -1;

    /**
     * The address book of this network.
     */
    private final AddressBook addressBook;
    /** The current epoch hash */
    private final Hash currentEpochHash;
    /** The current software version */
    private final SoftwareVersion currentSoftwareVersion;

    /**
     * Prevent log messages about a lack of signatures from spamming the logs.
     */
    private final RateLimiter lackingSignaturesRateLimiter;

    /**
     * Prevent log messages about self ISS events from spamming the logs.
     */
    private final RateLimiter selfIssRateLimiter;

    /**
     * Prevent log messages about catastrophic ISS events from spamming the logs.
     */
    private final RateLimiter catastrophicIssRateLimiter;

    /**
     * Use this constant if the consensus hash manager should not ignore any rounds.
     */
    public static final int DO_NOT_IGNORE_ROUNDS = -1;

    /**
     * A round that should not be validated. Set to {@link #DO_NOT_IGNORE_ROUNDS} if all rounds should be validated.
     */
    private final long ignoredRound;

    private final SelfIssTrigger selfIssDispatcher;
    private final CatastrophicIssTrigger catastrophicIssDispatcher;
    private final StateHashValidityTrigger stateHashValidityDispatcher;

    /**
     * Create an object that tracks reported hashes and detects ISS events.
     *
     * @param platformContext        the platform context
     * @param time                   provides the current wall clock time
     * @param dispatchBuilder        responsible for building dispatchers
     * @param addressBook            the address book for the network
     * @param currentEpochHash       the current epoch hash
     * @param currentSoftwareVersion the current software version
     * @param ignoredRound           a round that should not be validated. Set to {@link #DO_NOT_IGNORE_ROUNDS} if all
     *                               rounds should be validated.
     */
    public ConsensusHashManager(
            @NonNull final PlatformContext platformContext,
            final Time time,
            final DispatchBuilder dispatchBuilder,
            final AddressBook addressBook,
            final Hash currentEpochHash,
            final SoftwareVersion currentSoftwareVersion,
            final long ignoredRound) {

        final ConsensusConfig consensusConfig =
                platformContext.getConfiguration().getConfigData(ConsensusConfig.class);
        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);

        final Duration timeBetweenIssLogs = Duration.ofSeconds(stateConfig.secondsBetweenIssLogs());
        lackingSignaturesRateLimiter = new RateLimiter(time, timeBetweenIssLogs);
        selfIssRateLimiter = new RateLimiter(time, timeBetweenIssLogs);
        catastrophicIssRateLimiter = new RateLimiter(time, timeBetweenIssLogs);

        this.selfIssDispatcher = dispatchBuilder.getDispatcher(
                ConsensusHashManager.class, SelfIssTrigger.class, "self ISS detected")::dispatch;
        this.catastrophicIssDispatcher = dispatchBuilder.getDispatcher(
                ConsensusHashManager.class, CatastrophicIssTrigger.class, "really bad ISS detected")::dispatch;
        this.stateHashValidityDispatcher = dispatchBuilder.getDispatcher(
                ConsensusHashManager.class, StateHashValidityTrigger.class, "round ISS status known")::dispatch;

        this.addressBook = addressBook;
        this.currentEpochHash = currentEpochHash;
        this.currentSoftwareVersion = currentSoftwareVersion;

        this.roundData = new ConcurrentSequenceMap<>(
                -consensusConfig.roundsNonAncient(), consensusConfig.roundsNonAncient(), x -> x);

        this.ignoredRound = ignoredRound;
        if (ignoredRound != DO_NOT_IGNORE_ROUNDS) {
            logger.warn(STARTUP.getMarker(), "No ISS detection will be performed for round {}", ignoredRound);
        }
    }

    /**
     * Observes when a round has been completed.
     *
     * @param round the round that was just completed
     */
    public void roundCompleted(final long round) {
        if (round <= previousRound) {
            throw new IllegalArgumentException(
                    "previous round was " + previousRound + ", can't decrease round to " + round);
        }

        if (round == ignoredRound) {
            // This round is intentionally ignored.
            return;
        }

        final long oldestRoundToValidate = round - roundData.getSequenceNumberCapacity() + 1;

        if (round != previousRound + 1) {
            // We are either loading the first state at boot time, or we had a reconnect that caused us to skip some
            // rounds. Rounds that have not yet been validated at this point in time should not be considered
            // evidence of a catastrophic ISS.
            roundData.shiftWindow(oldestRoundToValidate);
        } else {
            roundData.shiftWindow(oldestRoundToValidate, this::handleRemovedRound);
        }

        final long roundWeight = addressBook.getTotalWeight();
        previousRound = round;
        roundData.put(round, new RoundHashValidator(stateHashValidityDispatcher, round, roundWeight));
    }

    /**
     * Handle a round that has become old enough that we want to stop tracking data on it.
     *
     * @param round              the round that is old
     * @param roundHashValidator the hash validator for the round
     */
    private void handleRemovedRound(final long round, final RoundHashValidator roundHashValidator) {
        final boolean justDecided = roundHashValidator.outOfTime();

        final StringBuilder sb = new StringBuilder();
        roundHashValidator.getHashFinder().writePartitionData(sb);
        logger.info(STATE_HASH.getMarker(), sb);

        if (justDecided) {
            final HashValidityStatus status = roundHashValidator.getStatus();
            if (status == HashValidityStatus.CATASTROPHIC_ISS
                    || status == HashValidityStatus.CATASTROPHIC_LACK_OF_DATA) {
                handleCatastrophic(roundHashValidator);
            } else if (status == HashValidityStatus.LACK_OF_DATA) {
                handleLackOfData(roundHashValidator);
            } else {
                throw new IllegalStateException(
                        "Unexpected hash validation status " + status + ", should have decided prior to now");
            }
        }
    }

    /**
     * <p>
     * Observes post-consensus state signature transactions.
     * </p>
     *
     * <p>
     * Since it is only possible to sign a round after it has reached consensus, it is guaranteed that any valid
     * signature transaction observed here (post consensus) will be for a round in the past.
     * </p>
     *
     * @param signerId             the ID of the node that signed the state
     * @param signatureTransaction the signature transaction
     * @param eventVersion         the version of the event that contains the transaction
     */
    public void handlePostconsensusSignatureTransaction(
            @NonNull final NodeId signerId,
            @NonNull final StateSignatureTransaction signatureTransaction,
            @Nullable final SoftwareVersion eventVersion) {

        Objects.requireNonNull(signerId);
        Objects.requireNonNull(signatureTransaction);

        if (!Objects.equals(currentSoftwareVersion, eventVersion)) {
            // this is a signature from a different software version, ignore it
            return;
        }

        if (!Objects.equals(signatureTransaction.getEpochHash(), currentEpochHash)) {
            // this is a signature from a different epoch, ignore it
            return;
        }

        if (!addressBook.contains(signerId)) {
            // we don't care about nodes not in the address book
            return;
        }

        if (signatureTransaction.getRound() == ignoredRound) {
            // This round is intentionally ignored.
            return;
        }

        final long nodeWeight = addressBook.getAddress(signerId).getWeight();

        final RoundHashValidator roundValidator = roundData.get(signatureTransaction.getRound());
        if (roundValidator == null) {
            // We are being asked to validate a signature from the far future or far past, or a round that has already
            // been decided.
            return;
        }

        final boolean decided =
                roundValidator.reportHashFromNetwork(signerId, nodeWeight, signatureTransaction.getStateHash());
        if (decided) {
            checkValidity(roundValidator);
        }
    }

    /**
     * Observe when this node finishes hashing a state.
     *
     * @param round the round of the state
     * @param hash  the hash of the state
     */
    @Observer(value = StateHashedTrigger.class, comment = "check hash derived by this node")
    public void stateHashedObserver(final Long round, final Hash hash) {
        if (round == ignoredRound) {
            // This round is intentionally ignored.
            return;
        }

        final RoundHashValidator roundHashValidator = roundData.get(round);
        if (roundHashValidator == null) {
            throw new IllegalStateException(
                    "Hash reported for round " + round + ", but that round is not being tracked");
        }

        final boolean decided = roundHashValidator.reportSelfHash(hash);
        if (decided) {
            checkValidity(roundHashValidator);
        }
    }

    /**
     * Observe when an overriding state is obtained, i.e. via reconnect or state loading.
     *
     * @param round     the round of the state that was obtained
     * @param stateHash the hash of the state that was obtained
     */
    @Observer(
            value = {DiskStateLoadedTrigger.class, ReconnectStateLoadedTrigger.class},
            comment = "ingest completed state")
    public void overridingStateObserver(final Long round, final Hash stateHash) {
        roundCompleted(round);
        stateHashedObserver(round, stateHash);
    }

    /**
     * Called once the validity has been decided. Take action based on the validity status.
     *
     * @param roundValidator the validator for the round
     */
    private void checkValidity(final RoundHashValidator roundValidator) {
        final long round = roundValidator.getRound();

        switch (roundValidator.getStatus()) {
            case VALID -> {
                // :)
            }
            case SELF_ISS -> handleSelfIss(roundValidator);
            case CATASTROPHIC_ISS -> handleCatastrophic(roundValidator);
            case UNDECIDED -> throw new IllegalStateException(
                    "status is undecided, but method reported a decision, round = " + round);
            case LACK_OF_DATA -> throw new IllegalStateException(
                    "a decision that we lack data should only be possible once time runs out, round = " + round);
            default -> throw new IllegalStateException(
                    "unhandled case " + roundValidator.getStatus() + ", round = " + round);
        }
    }

    /**
     * This node doesn't agree with the consensus hash.
     *
     * @param roundHashValidator the validator responsible for validating the round with a self ISS
     */
    private void handleSelfIss(final RoundHashValidator roundHashValidator) {
        final long round = roundHashValidator.getRound();
        final Hash selfHash = roundHashValidator.getSelfStateHash();
        final Hash consensusHash = roundHashValidator.getConsensusHash();

        final long skipCount = selfIssRateLimiter.getDeniedRequests();
        if (selfIssRateLimiter.requestAndTrigger()) {

            final StringBuilder sb = new StringBuilder();
            sb.append("Invalid State Signature (ISS): this node has the wrong hash for round ")
                    .append(round)
                    .append(".\n");

            roundHashValidator.getHashFinder().writePartitionData(sb);
            writeSkippedLogCount(sb, skipCount);

            logger.fatal(
                    EXCEPTION.getMarker(),
                    new IssPayload(sb.toString(), round, selfHash.toMnemonic(), consensusHash.toMnemonic(), false));
        }

        selfIssDispatcher.dispatch(round, selfHash, consensusHash);
    }

    /**
     * There has been a catastrophic ISS or a catastrophic lack of data.
     *
     * @param roundHashValidator information about the round, including the signatures that were gathered
     */
    private void handleCatastrophic(final RoundHashValidator roundHashValidator) {

        final long round = roundHashValidator.getRound();
        final ConsensusHashFinder hashFinder = roundHashValidator.getHashFinder();
        final Hash selfHash = roundHashValidator.getSelfStateHash();

        final long skipCount = catastrophicIssRateLimiter.getDeniedRequests();
        if (catastrophicIssRateLimiter.requestAndTrigger()) {

            final StringBuilder sb = new StringBuilder();
            sb.append("Catastrophic Invalid State Signature (ISS)\n");
            sb.append("Due to divergence in state hash between many network members, "
                    + "this network is incapable of continued operation without human intervention.\n");

            hashFinder.writePartitionData(sb);
            writeSkippedLogCount(sb, skipCount);

            logger.fatal(EXCEPTION.getMarker(), new IssPayload(sb.toString(), round, selfHash.toMnemonic(), "", true));
        }

        catastrophicIssDispatcher.dispatch(round, selfHash);
    }

    /**
     * We are not getting the signatures we need to be getting. ISS events may be going undetected.
     *
     * @param roundHashValidator information about the round
     */
    private void handleLackOfData(final RoundHashValidator roundHashValidator) {
        final long skipCount = lackingSignaturesRateLimiter.getDeniedRequests();
        if (!lackingSignaturesRateLimiter.requestAndTrigger()) {
            return;
        }

        final long round = roundHashValidator.getRound();
        final ConsensusHashFinder hashFinder = roundHashValidator.getHashFinder();
        final Hash selfHash = roundHashValidator.getSelfStateHash();

        final StringBuilder sb = new StringBuilder();
        sb.append("Unable to collect enough data to determine the consensus hash for round ")
                .append(round)
                .append(".\n");
        if (selfHash == null) {
            sb.append("No self hash was computed. This is highly unusual.\n");
        }
        hashFinder.writePartitionData(sb);
        writeSkippedLogCount(sb, skipCount);

        logger.warn(STATE_HASH.getMarker(), sb);
    }

    /**
     * Write the number of times a log has been skipped.
     */
    private static void writeSkippedLogCount(final StringBuilder sb, final long skipCount) {
        if (skipCount > 0) {
            sb.append("This condition has been triggered ")
                    .append(skipCount)
                    .append(" time(s) over the last ")
                    .append(Duration.ofMinutes(1).toSeconds())
                    .append("seconds.");
        }
    }
}
