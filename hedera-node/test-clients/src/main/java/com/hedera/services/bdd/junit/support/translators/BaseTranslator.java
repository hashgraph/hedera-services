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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.streams.CallOperationType.OP_DELEGATECALL;
import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.LAZY_MEMO;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.AUTO_MEMO;
import static com.hedera.node.config.types.EntityType.ACCOUNT;
import static com.hedera.node.config.types.EntityType.FILE;
import static com.hedera.node.config.types.EntityType.SCHEDULE;
import static com.hedera.node.config.types.EntityType.TOKEN;
import static com.hedera.node.config.types.EntityType.TOPIC;
import static com.hedera.services.bdd.junit.support.translators.impl.TranslatorUtils.addSyntheticResultIfExpected;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.CreateContractOutput;
import com.hedera.hapi.block.stream.output.EthereumOutput;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActions;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements shared translation logic for transaction records, in particular providing a
 * source of token types by {@link com.hedera.hapi.node.base.TokenID}.
 */
public class BaseTranslator {
    private static final Logger log = LogManager.getLogger(BaseTranslator.class);

    private static final Set<String> AUTO_CREATION_MEMOS = Set.of(LAZY_MEMO, AUTO_MEMO);
    private static final long EXCHANGE_RATES_FILE_NUM = 112L;

    private long highestKnownEntityNum = 0L;
    private ExchangeRateSet activeRates;
    private Instant userTimestamp;
    private ScheduleID scheduleRef;
    private BlockTransactionalUnit unit;

    private final List<TransactionSidecarRecord> sidecarRecords = new ArrayList<>();
    private final List<ContractAction> contractActions = new ArrayList<>();
    private final Map<EntityType, List<Long>> nextCreatedNums = new EnumMap<>(EntityType.class);
    private final Map<TokenID, TokenType> tokenTypes = new HashMap<>();
    private final Map<TokenID, Long> totalSupplies = new HashMap<>();
    private final Map<TokenID, Integer> numMints = new HashMap<>();
    private final Map<TokenID, List<Long>> highestPutSerialNos = new HashMap<>();
    private final Set<TokenAssociation> knownAssociations = new HashSet<>();

    /**
     * Defines how a translator specifies details of a translated transaction record.
     */
    @FunctionalInterface
    public interface Spec {
        void accept(
                @NonNull TransactionReceipt.Builder receiptBuilder,
                @NonNull TransactionRecord.Builder recordBuilder,
                @NonNull Consumer<TokenID> involvedTokenId);
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
     * Detects new token types from the given state changes.
     *
     * @param unit the unit to prepare for
     */
    public void prepareForUnit(@NonNull final BlockTransactionalUnit unit) {
        this.unit = unit;
        numMints.clear();
        highestPutSerialNos.clear();
        nextCreatedNums.clear();
        sidecarRecords.clear();
        contractActions.clear();
        scheduleRef = null;
        scanUnit(unit);
        sidecarRecords.stream()
                .map(sidecar -> sidecar.actionsOrElse(ContractActions.DEFAULT))
                .map(ContractActions::contractActions)
                .forEach(contractActions::addAll);
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
        highestKnownEntityNum =
                nextCreatedNums.values().stream().mapToLong(List::getLast).max().orElse(highestKnownEntityNum);
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
     * Returns the next synthetic call result for a system contract call.
     *
     * @return the next synthetic call result
     */
    public ContractFunctionResult nextSyntheticCallResult() {
        int i = 0;
        ContractFunctionResult result = null;
        for (int n = contractActions.size(); i < n; i++) {
            final var action = contractActions.get(i);
            if (isSystem(action.recipientContractOrElse(ContractID.DEFAULT))) {
                final var builder = ContractFunctionResult.newBuilder()
                        .contractID(action.recipientContractOrThrow())
                        .amount(action.value())
                        .functionParameters(action.input())
                        .gas(action.gas())
                        .gasUsed(action.gasUsed());
                switch (action.resultData().kind()) {
                    case UNSET -> throw new IllegalStateException("No result data in synthetic call");
                    case OUTPUT -> builder.contractCallResult(action.outputOrThrow());
                    case REVERT_REASON -> builder.errorMessage(
                            action.revertReasonOrThrow().asUtf8String());
                    case ERROR -> builder.errorMessage(action.errorOrThrow().asUtf8String());
                }
                int j = i;
                for (; j >= 0 && contractActions.get(j).callOperationType() == OP_DELEGATECALL; j--) {
                    // Skip all these, DELEGATECALL does not change sender address
                }
                final var senderAction = contractActions.get(j);
                switch (senderAction.caller().kind()) {
                    case UNSET -> throw new IllegalStateException("No caller in synthetic call");
                    case CALLING_ACCOUNT -> builder.senderId(senderAction.callingAccountOrThrow());
                    case CALLING_CONTRACT -> builder.senderId(AccountID.newBuilder()
                            .accountNum(senderAction.callingContractOrThrow().contractNumOrThrow())
                            .build());
                }
                result = builder.build();
                break;
            }
        }
        if (result != null) {
            contractActions.remove(i);
            return result;
        } else {
            throw new IllegalStateException("No more synthetic call results available - " + contractActions);
        }
    }

    private static final long HTS_SYSTEM_CONTRACT_NUM = 0x167;
    private static final long HAS_SYSTEM_CONTRACT_NUM = 0x16a;

    private boolean isSystem(@NonNull final ContractID contractID) {
        final long num = contractID.contractNumOrThrow();
        return num == HTS_SYSTEM_CONTRACT_NUM || num == HAS_SYSTEM_CONTRACT_NUM;
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
        if (!followsUserRecord || AUTO_CREATION_MEMOS.contains(parts.memo())) {
            // Only preceding and user transactions get exchange rates in their receipts; note that
            // auto-account creations are always preceding dispatches and so get exchange rates
            receiptBuilder.exchangeRate(activeRates);
        }
        final AtomicReference<TokenType> tokenType = new AtomicReference<>();
        final List<TransactionSidecarRecord> sidecarRecords = new ArrayList<>();
        spec.accept(receiptBuilder, recordBuilder, tokenId -> tokenType.set(tokenTypes.get(tokenId)));
        addSyntheticResultIfExpected(this, parts, recordBuilder);
        if (parts.transactionIdOrThrow().scheduled()) {
            try {
                recordBuilder.scheduleRef(scheduleRefOrThrow());
            } catch (Exception e) {
                log.error(
                        "Failed to add schedule ref to transaction record for {} - state changes were {}",
                        parts.body(),
                        unit.stateChanges(),
                        e);
            }
        }
        return new SingleTransactionRecord(
                parts.transactionParts().wrapper(),
                recordBuilder.receipt(receiptBuilder.build()).build(),
                sidecarRecords,
                new SingleTransactionRecord.TransactionOutputs(tokenType.get()));
    }

    /**
     * Determines if the given parts are following the user timestamp.
     *
     * @param parts the parts to check
     * @return true if the parts are following the user timestamp
     */
    public boolean isFollowingChild(@NonNull final BlockTransactionParts parts) {
        return asInstant(parts.consensusTimestamp()).isAfter(userTimestamp);
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
            if (stateChange.hasMapUpdate()) {
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
                    } else if (num == EXCHANGE_RATES_FILE_NUM) {
                        updateActiveRates(stateChange);
                    }
                } else if (key.hasScheduleIdKey()) {
                    final var num = key.scheduleIdKeyOrThrow().scheduleNum();
                    if (num > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(SCHEDULE, ignore -> new LinkedList<>())
                                .add(num);
                    }
                    scheduleRef = key.scheduleIdKeyOrThrow();
                } else if (key.hasContractIdKey() || key.hasAccountIdKey()) {
                    final var num = key.hasContractIdKey()
                            ? key.contractIdKeyOrThrow().contractNumOrThrow()
                            : key.accountIdKeyOrThrow().accountNumOrThrow();
                    if (num > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(ACCOUNT, ignore -> new LinkedList<>())
                                .add(num);
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
                if (parts.transactionIdOrThrow()
                        .transactionValidStartOrThrow()
                        .equals(new Timestamp(1725025158L, 5096))) {
                    log.info("User timestamp set to {} for {} ({})", userTimestamp, parts.body(), parts.status());
                }
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
                case CONTRACT_CALL -> Optional.ofNullable(parts.transactionOutput())
                        .map(TransactionOutput::contractCall)
                        .map(CallContractOutput::sidecars)
                        .ifPresent(sidecarRecords::addAll);
                case CONTRACT_CREATE -> Optional.ofNullable(parts.transactionOutput())
                        .map(TransactionOutput::contractCreate)
                        .map(CreateContractOutput::sidecars)
                        .ifPresent(sidecarRecords::addAll);
                case ETHEREUM_TRANSACTION -> Optional.ofNullable(parts.transactionOutput())
                        .map(TransactionOutput::ethereumCall)
                        .map(EthereumOutput::sidecars)
                        .ifPresent(sidecarRecords::addAll);
            }
        });
    }

    private void updateActiveRates(@NonNull final StateChange change) {
        final var contents =
                change.mapUpdateOrThrow().valueOrThrow().fileValueOrThrow().contents();
        try {
            activeRates = ExchangeRateSet.PROTOBUF.parse(contents);
        } catch (ParseException e) {
            throw new IllegalStateException("Rates file updated with unparseable contents", e);
        }
    }
}
