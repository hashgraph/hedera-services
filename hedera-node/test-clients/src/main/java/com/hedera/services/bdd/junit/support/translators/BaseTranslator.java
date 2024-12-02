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

package com.hedera.services.bdd.junit.support.translators;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.config.types.EntityType.ACCOUNT;
import static com.hedera.node.config.types.EntityType.FILE;
import static com.hedera.node.config.types.EntityType.NODE;
import static com.hedera.node.config.types.EntityType.SCHEDULE;
import static com.hedera.node.config.types.EntityType.TOKEN;
import static com.hedera.node.config.types.EntityType.TOPIC;
import static com.hedera.services.bdd.junit.support.translators.impl.FileUpdateTranslator.EXCHANGE_RATES_FILE_NUM;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.CreateContractOutput;
import com.hedera.hapi.block.stream.output.EthereumOutput;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.types.EntityType;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionalUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements shared translation logic for transaction records, maintaining all the extra-stream
 * context needed to recover the traditional record stream from a block stream.
 */
public class BaseTranslator {
    private static final Logger log = LogManager.getLogger(BaseTranslator.class);

    /**
     * These fields are context maintained for the full lifetime of the translator.
     */
    private long highestKnownEntityNum = 0L;

    private long highestKnownNodeId;

    private ExchangeRateSet activeRates;
    private final Map<TokenID, Long> totalSupplies = new HashMap<>();
    private final Map<TokenID, TokenType> tokenTypes = new HashMap<>();
    private final Set<TokenAssociation> knownAssociations = new HashSet<>();
    private final Map<PendingAirdropId, PendingAirdropValue> pendingAirdrops = new HashMap<>();

    /**
     * These fields are used to translate a single "unit" of block items connected to a {@link TransactionID}.
     */
    private long prevHighestKnownEntityNum = 0L;

    private Instant userTimestamp;
    private ScheduleID scheduleRef;
    private final List<TransactionSidecarRecord> sidecarRecords = new ArrayList<>();
    private final Map<TokenID, Integer> numMints = new HashMap<>();
    private final Map<TokenID, List<Long>> highestPutSerialNos = new HashMap<>();
    private final Map<EntityType, List<Long>> nextCreatedNums = new EnumMap<>(EntityType.class);

    /**
     * Defines how a translator specifies details of a translated transaction record.
     */
    @FunctionalInterface
    public interface Spec {
        void accept(
                @NonNull TransactionReceipt.Builder receiptBuilder, @NonNull TransactionRecord.Builder recordBuilder);
    }

    /**
     * Constructs a translator with the given highest known node ID.
     * @param highestKnownNodeId the highest known node ID
     */
    public BaseTranslator(final long highestKnownNodeId) {
        this.highestKnownNodeId = highestKnownNodeId;
    }

    /**
     * Scans a block for genesis information and returns true if found.
     *
     * @param block the block to scan
     * @return true if genesis information was found
     */
    public boolean scanMaybeGenesisBlock(@NonNull final Block block) {
        for (final var item : block.items()) {
            if (item.hasStateChanges()) {
                for (final var change : item.stateChangesOrThrow().stateChanges()) {
                    if (change.hasMapUpdate()
                            && change.mapUpdateOrThrow().keyOrThrow().hasFileIdKey()) {
                        final var fileNum = change.mapUpdateOrThrow()
                                .keyOrThrow()
                                .fileIdKeyOrThrow()
                                .fileNum();
                        if (fileNum == EXCHANGE_RATES_FILE_NUM) {
                            updateActiveRates(change);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Provides the token type for the given token ID.
     *
     * @param tokenId the token ID to query
     * @return the token type
     */
    public @NonNull TokenType tokenTypeOrThrow(@NonNull final TokenID tokenId) {
        return tokenTypes.get(tokenId);
    }

    /**
     * Detects new token types from the given state changes.
     *
     * @param unit the unit to prepare for
     */
    public void prepareForUnit(@NonNull final BlockTransactionalUnit unit) {
        this.prevHighestKnownEntityNum = highestKnownEntityNum;
        numMints.clear();
        highestPutSerialNos.clear();
        nextCreatedNums.clear();
        sidecarRecords.clear();
        scheduleRef = null;
        scanUnit(unit);
        nextCreatedNums.values().forEach(list -> {
            final Set<Long> distinctNums = Set.copyOf(list);
            list.clear();
            list.addAll(distinctNums);
            list.sort(Comparator.naturalOrder());
        });
        highestPutSerialNos.forEach((tokenId, serialNos) -> {
            final Set<Long> distinctSerialNos = Set.copyOf(serialNos);
            final var mintedHere = new ArrayList<>(distinctSerialNos);
            mintedHere.sort(Collections.reverseOrder());
            serialNos.clear();
            serialNos.addAll(mintedHere.subList(0, numMints.getOrDefault(tokenId, 0)));
            serialNos.sort(Comparator.naturalOrder());
        });
        if (nextCreatedNums.containsKey(NODE)) {
            highestKnownNodeId = nextCreatedNums.get(NODE).getLast();
        }
        highestKnownEntityNum =
                nextCreatedNums.values().stream().mapToLong(List::getLast).max().orElse(highestKnownEntityNum);
    }

    /**
     * Determines if the given number was created in the ongoing transactional unit.
     *
     * @param num the number to query
     * @return true if the number was created
     */
    public boolean entityCreatedThisUnit(final long num) {
        return num > prevHighestKnownEntityNum;
    }

    /**
     * Tracks the association of a token with an account.
     *
     * @param tokenID the token to track
     * @param accountID the account to track
     */
    public void trackAssociation(@NonNull final TokenID tokenID, @NonNull final AccountID accountID) {
        knownAssociations.add(new TokenAssociation(tokenID, accountID));
    }

    /**
     * Tracks the dissociation of a token from an account.
     *
     * @param tokenID the token to track
     * @param accountID the account to track
     */
    public void trackDissociation(@NonNull final TokenID tokenID, @NonNull final AccountID accountID) {
        knownAssociations.add(new TokenAssociation(tokenID, accountID));
    }

    /**
     * Initializes the total supply of the given token.
     *
     * @param tokenId the token to initialize
     * @param totalSupply the total supply to set
     */
    public void initTotalSupply(@NonNull final TokenID tokenId, final long totalSupply) {
        totalSupplies.put(tokenId, totalSupply);
    }

    /**
     * Adjusts the total supply of the given token by the given amount and returns the new total supply.
     *
     * @param tokenId the token to adjust
     * @param adjustment the amount to adjust by
     * @return the new total supply
     */
    public long newTotalSupply(@NonNull final TokenID tokenId, final long adjustment) {
        return totalSupplies.merge(tokenId, adjustment, Long::sum);
    }

    /**
     * Determines if the given token was already associated with the given account before the ongoing
     * transactional unit being translated into records.
     *
     * @param tokenId the token to query
     * @param accountId the account to query
     * @return true if the token was already associated with the account
     */
    public boolean wasAlreadyAssociated(@NonNull final TokenID tokenId, @NonNull final AccountID accountId) {
        requireNonNull(tokenId);
        requireNonNull(accountId);
        return knownAssociations.contains(new TokenAssociation(tokenId, accountId));
    }

    /**
     * Provides the next {@code n} serial numbers that were minted for the given token in the transactional unit.
     *
     * @param tokenId the token to query
     * @param n the number of serial numbers to provide
     * @return the next {@code n} serial numbers that were minted for the token
     */
    public List<Long> nextNMints(@NonNull final TokenID tokenId, final int n) {
        final var serialNos = highestPutSerialNos.get(tokenId);
        if (serialNos == null) {
            log.error("No serial numbers found for token {}", tokenId);
            return emptyList();
        }
        if (n > serialNos.size()) {
            log.error("Only {} serial numbers found for token {}, not the requested {}", serialNos.size(), tokenId, n);
            return emptyList();
        }
        final var mints = new ArrayList<>(serialNos.subList(0, n));
        serialNos.removeAll(mints);
        return mints;
    }

    /**
     * Provides the next created entity number of the given type in the ongoing transactional unit.
     *
     * @param type the type of entity
     * @return the next created entity number
     */
    public long nextCreatedNum(@NonNull final EntityType type) {
        final var createdNums = nextCreatedNums.getOrDefault(type, Collections.emptyList());
        if (createdNums.isEmpty()) {
            log.error("No created numbers found for entity type {}", type);
            return -1L;
        }
        return nextCreatedNums.get(type).removeFirst();
    }

    /**
     * Tracks the given pending airdrop record if it was not already in the set of known pending airdrops.
     *
     * @param pendingAirdropRecord the pending airdrop record to track
     * @return true if the record was tracked
     */
    public boolean track(@NonNull final PendingAirdropRecord pendingAirdropRecord) {
        final var airdropId = pendingAirdropRecord.pendingAirdropIdOrThrow();
        final var currentValue = pendingAirdrops.get(airdropId);
        final var newValue = pendingAirdropRecord.pendingAirdropValue();
        final var changed = !pendingAirdrops.containsKey(airdropId) || !Objects.equals(currentValue, newValue);
        pendingAirdrops.put(airdropId, newValue);
        return changed;
    }

    /**
     * Removes the given pending airdrop record from the set of known pending airdrops.
     *
     * @param pendingAirdropId the id to remove
     */
    public void remove(@NonNull final PendingAirdropId pendingAirdropId) {
        pendingAirdrops.remove(pendingAirdropId);
    }

    /**
     * Given a {@link BlockTransactionParts} and a {@link Spec}, translates the implied {@link SingleTransactionRecord}.
     *
     * @param parts the parts of the transaction
     * @param spec the specification of the transaction record
     * @return the translated record
     */
    public SingleTransactionRecord recordFrom(@NonNull final BlockTransactionParts parts, @NonNull final Spec spec) {
        final var txnId = parts.transactionIdOrThrow();
        final var recordBuilder = TransactionRecord.newBuilder()
                .transactionHash(parts.transactionHash())
                .consensusTimestamp(parts.consensusTimestamp())
                .transactionID(txnId)
                .memo(parts.memo())
                .transactionFee(parts.transactionFee())
                .transferList(parts.transferList())
                .tokenTransferLists(parts.tokenTransferLists())
                .automaticTokenAssociations(parts.automaticTokenAssociations())
                .paidStakingRewards(parts.paidStakingRewards());
        final var receiptBuilder =
                TransactionReceipt.newBuilder().status(parts.transactionResult().status());
        final boolean followsUserRecord = asInstant(parts.consensusTimestamp()).isAfter(userTimestamp);
        if (followsUserRecord && !parts.transactionIdOrThrow().scheduled()) {
            recordBuilder.parentConsensusTimestamp(asTimestamp(userTimestamp));
        }
        if (!followsUserRecord || parts.transactionIdOrThrow().scheduled()) {
            // Only preceding and user transactions get exchange rates in their receipts; note that
            // auto-account creations are always preceding dispatches and so get exchange rates
            receiptBuilder.exchangeRate(activeRates);
        }
        spec.accept(receiptBuilder, recordBuilder);
        if (!isContractOp(parts) && parts.hasContractOutput()) {
            final var output = parts.callContractOutputOrThrow();
            recordBuilder.contractCallResult(output.contractCallResultOrThrow());
        }
        if (parts.transactionIdOrThrow().scheduled()) {
            recordBuilder.scheduleRef(scheduleRefOrThrow());
        }
        return new SingleTransactionRecord(
                parts.transactionParts().wrapper(),
                recordBuilder.receipt(receiptBuilder.build()).build(),
                sidecarRecords,
                new SingleTransactionRecord.TransactionOutputs(null));
    }

    /**
     * Updates the active exchange rates with the contents of the given state change.
     * @param change the state change to update from
     */
    public void updateActiveRates(@NonNull final StateChange change) {
        final var contents =
                change.mapUpdateOrThrow().valueOrThrow().fileValueOrThrow().contents();
        try {
            activeRates = ExchangeRateSet.PROTOBUF.parse(contents);
            log.info("Updated active exchange rates to {}", activeRates);
        } catch (ParseException e) {
            throw new IllegalStateException("Rates file updated with unparseable contents", e);
        }
    }

    /**
     * Returns the active exchange rates.
     * @return the active exchange rates
     */
    public ExchangeRateSet activeRates() {
        return activeRates;
    }

    /**
     * Returns the modified schedule id for the ongoing transactional unit.
     *
     * @return the modified schedule id
     */
    public @NonNull ScheduleID scheduleRefOrThrow() {
        return requireNonNull(scheduleRef);
    }

    private void scanUnit(@NonNull final BlockTransactionalUnit unit) {
        unit.stateChanges().forEach(stateChange -> {
            if (stateChange.hasMapDelete()) {
                final var mapDelete = stateChange.mapDeleteOrThrow();
                final var key = mapDelete.keyOrThrow();
                if (key.hasScheduleIdKey()) {
                    scheduleRef = key.scheduleIdKeyOrThrow();
                }
            } else if (stateChange.hasMapUpdate()) {
                final var mapUpdate = stateChange.mapUpdateOrThrow();
                final var key = mapUpdate.keyOrThrow();
                if (key.hasTokenIdKey()) {
                    final var tokenId = mapUpdate.keyOrThrow().tokenIdKeyOrThrow();
                    if (!tokenTypes.containsKey(tokenId)) {
                        tokenTypes.put(
                                tokenId,
                                mapUpdate.valueOrThrow().tokenValueOrThrow().tokenType());
                    }
                    if (tokenId.tokenNum() > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(TOKEN, ignore -> new LinkedList<>())
                                .add(tokenId.tokenNum());
                    }
                } else if (key.hasTopicIdKey()) {
                    final var num = key.topicIdKeyOrThrow().topicNum();
                    if (num > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(TOPIC, ignore -> new LinkedList<>())
                                .add(num);
                    }
                } else if (key.hasFileIdKey()) {
                    final var num = key.fileIdKeyOrThrow().fileNum();
                    if (num > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(FILE, ignore -> new LinkedList<>())
                                .add(num);
                    }
                } else if (key.hasScheduleIdKey()) {
                    final var num = key.scheduleIdKeyOrThrow().scheduleNum();
                    if (num > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(SCHEDULE, ignore -> new LinkedList<>())
                                .add(num);
                    }
                    scheduleRef = key.scheduleIdKeyOrThrow();
                } else if (key.hasAccountIdKey()) {
                    final var num = key.accountIdKeyOrThrow().accountNumOrThrow();
                    if (num > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(ACCOUNT, ignore -> new LinkedList<>())
                                .add(num);
                    }
                } else if (key.hasEntityNumberKey()) {
                    final var value = mapUpdate.valueOrThrow();
                    if (value.hasNodeValue()) {
                        final long nodeId = key.entityNumberKeyOrThrow();
                        if (nodeId > highestKnownNodeId) {
                            nextCreatedNums
                                    .computeIfAbsent(NODE, ignore -> new LinkedList<>())
                                    .add(nodeId);
                        }
                    }
                } else if (key.hasNftIdKey()) {
                    final var nftId = key.nftIdKeyOrThrow();
                    final var tokenId = nftId.tokenId();
                    highestPutSerialNos
                            .computeIfAbsent(tokenId, ignore -> new LinkedList<>())
                            .add(nftId.serialNumber());
                }
            }
        });
        unit.blockTransactionParts().forEach(parts -> {
            if (parts.transactionIdOrThrow().nonce() == 0
                    && !parts.transactionIdOrThrow().scheduled()) {
                userTimestamp = asInstant(parts.consensusTimestamp());
            }
            switch (parts.functionality()) {
                case TOKEN_MINT -> {
                    if (parts.status() == SUCCESS) {
                        final var op = parts.body().tokenMintOrThrow();
                        final var numMetadata = op.metadata().size();
                        if (numMetadata > 0) {
                            final var tokenId = op.tokenOrThrow();
                            numMints.merge(tokenId, numMetadata, Integer::sum);
                        }
                    }
                }
                case CONTRACT_CALL -> parts.outputIfPresent(TransactionOutput.TransactionOneOfType.CONTRACT_CALL)
                        .map(TransactionOutput::contractCall)
                        .map(CallContractOutput::sidecars)
                        .ifPresent(sidecarRecords::addAll);
                case CONTRACT_CREATE -> parts.outputIfPresent(TransactionOutput.TransactionOneOfType.CONTRACT_CREATE)
                        .map(TransactionOutput::contractCreate)
                        .map(CreateContractOutput::sidecars)
                        .ifPresent(sidecarRecords::addAll);
                case ETHEREUM_TRANSACTION -> parts.outputIfPresent(TransactionOutput.TransactionOneOfType.ETHEREUM_CALL)
                        .map(TransactionOutput::ethereumCall)
                        .map(EthereumOutput::sidecars)
                        .ifPresent(sidecarRecords::addAll);
            }
        });
    }

    private static boolean isContractOp(@NonNull final BlockTransactionParts parts) {
        final var function = parts.functionality();
        return function == CONTRACT_CALL || function == CONTRACT_CREATE || function == ETHEREUM_TRANSACTION;
    }
}
