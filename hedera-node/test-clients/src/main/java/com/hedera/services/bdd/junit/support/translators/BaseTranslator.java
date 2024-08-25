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

import static com.hedera.node.config.types.EntityType.ACCOUNT;
import static com.hedera.node.config.types.EntityType.FILE;
import static com.hedera.node.config.types.EntityType.TOKEN;
import static com.hedera.node.config.types.EntityType.TOPIC;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.types.EntityType;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionalUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Implements shared translation logic for transaction records, in particular providing a
 * source of token types by {@link com.hedera.hapi.node.base.TokenID}.
 */
public class BaseTranslator {
    private long highestKnownEntityNum = 0L;
    private final Map<EntityType, List<Long>> nextCreatedNums = new EnumMap<>(EntityType.class);
    private final Map<TokenID, TokenType> tokenTypes = new HashMap<>();

    /**
     * Defines how a translator specifies details of a translated transaction record.
     */
    @FunctionalInterface
    public interface Spec {
        void accept(
                @NonNull TransactionReceipt.Builder receiptBuilder,
                @NonNull TransactionRecord.Builder recordBuilder,
                @NonNull List<TransactionSidecarRecord> sidecarRecords,
                @NonNull Consumer<TokenID> involvedTokenId);
    }

    /**
     * Detects new token types from the given state changes.
     *
     * @param unit the unit to prepare for
     */
    public void prepareForUnit(@NonNull final BlockTransactionalUnit unit) {
        nextCreatedNums.clear();
        scanCreationsIn(unit);
        nextCreatedNums.values().forEach(list -> list.sort(Comparator.naturalOrder()));
        highestKnownEntityNum =
                nextCreatedNums.values().stream().mapToLong(List::getLast).max().orElse(highestKnownEntityNum);
    }

    /**
     * Provides the next created entity number of the given type in the ongoing transactional unit.
     * @param type the type of entity
     * @return the next created entity number
     */
    public long nextCreatedNum(@NonNull final EntityType type) {
        return nextCreatedNums.get(type).removeFirst();
    }

    /**
     * Given a {@link BlockTransactionParts} and a {@link Spec}, translates the implied {@link SingleTransactionRecord}.
     * @param parts the parts of the transaction
     * @param spec the specification of the transaction record
     * @return the translated record
     */
    public SingleTransactionRecord recordFrom(@NonNull final BlockTransactionParts parts, @NonNull final Spec spec) {
        final var recordBuilder = TransactionRecord.newBuilder()
                .memo(parts.memo())
                .consensusTimestamp(parts.consensusTimestamp())
                .transactionFee(parts.transactionFee())
                .transferList(parts.transferList())
                .tokenTransferLists(parts.tokenTransferLists())
                .transactionHash(parts.transactionHash());
        final var receiptBuilder =
                TransactionReceipt.newBuilder().status(parts.transactionResult().status());
        final AtomicReference<TokenType> tokenType = new AtomicReference<>();
        final List<TransactionSidecarRecord> sidecarRecords = new ArrayList<>();
        spec.accept(receiptBuilder, recordBuilder, sidecarRecords, tokenId -> tokenType.set(tokenTypes.get(tokenId)));
        return new SingleTransactionRecord(
                parts.transactionParts().wrapper(),
                recordBuilder.receipt(receiptBuilder.build()).build(),
                sidecarRecords,
                new SingleTransactionRecord.TransactionOutputs(tokenType.get()));
    }

    private void scanCreationsIn(@NonNull final BlockTransactionalUnit unit) {
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
                    }
                } else if (key.hasContractIdKey() || key.hasAccountIdKey()) {
                    final var num = key.hasContractIdKey()
                            ? key.contractIdKeyOrThrow().contractNumOrThrow()
                            : key.accountIdKeyOrThrow().accountNumOrThrow();
                    if (num > highestKnownEntityNum) {
                        nextCreatedNums
                                .computeIfAbsent(ACCOUNT, ignore -> new LinkedList<>())
                                .add(num);
                    }
                }
            }
        });
    }
}
