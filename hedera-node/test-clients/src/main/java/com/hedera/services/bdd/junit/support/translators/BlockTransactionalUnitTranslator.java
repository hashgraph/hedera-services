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

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_CREATE_TOPIC;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_DELETE_TOPIC;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_UPDATE_TOPIC;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_DELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_APPEND;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.FREEZE;
import static com.hedera.hapi.node.base.HederaFunctionality.NODE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.NODE_STAKE_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_BURN;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_MINT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.UTIL_PRNG;
import static com.hedera.services.bdd.junit.support.translators.impl.NoExplicitSideEffectsTranslator.NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.impl.ContractCallTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.ContractCreateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.ContractDeleteTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.CryptoCreateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.CryptoTransferTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.FileCreateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.NodeCreateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.ScheduleCreateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.SubmitMessageTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.TokenBurnTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.TokenCreateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.TokenMintTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.TokenUpdateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.TopicCreateTranslator;
import com.hedera.services.bdd.junit.support.translators.impl.UtilPrngTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionalUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Defines a translator for a {@link BlockTransactionalUnit} into a list of {@link SingleTransactionRecord}s.
 */
public class BlockTransactionalUnitTranslator {
    private static final Logger log = LogManager.getLogger(BlockTransactionalUnitTranslator.class);

    /**
     * The base translator used to create the {@link SingleTransactionRecord}s.
     */
    private final BaseTranslator baseTranslator = new BaseTranslator();
    /**
     * The translators used to translate the block transaction parts for a logical HAPI transaction.
     */
    private final Map<HederaFunctionality, BlockTransactionPartsTranslator> translators =
            new EnumMap<>(HederaFunctionality.class) {
                {
                    put(CONSENSUS_CREATE_TOPIC, new TopicCreateTranslator());
                    put(CONSENSUS_DELETE_TOPIC, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(CONSENSUS_SUBMIT_MESSAGE, new SubmitMessageTranslator());
                    put(CONSENSUS_UPDATE_TOPIC, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(CONTRACT_CALL, new ContractCallTranslator());
                    put(CONTRACT_CREATE, new ContractCreateTranslator());
                    put(CONTRACT_DELETE, new ContractDeleteTranslator());
                    put(CRYPTO_APPROVE_ALLOWANCE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(CRYPTO_CREATE, new CryptoCreateTranslator());
                    put(CRYPTO_TRANSFER, new CryptoTransferTranslator());
                    put(CRYPTO_UPDATE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(FILE_APPEND, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(FILE_CREATE, new FileCreateTranslator());
                    put(FILE_UPDATE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(FREEZE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(NODE_CREATE, new NodeCreateTranslator());
                    put(NODE_STAKE_UPDATE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(SCHEDULE_CREATE, new ScheduleCreateTranslator());
                    put(TOKEN_ASSOCIATE_TO_ACCOUNT, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
                    put(TOKEN_CREATE, new TokenCreateTranslator());
                    put(TOKEN_UPDATE, new TokenUpdateTranslator());
                    put(TOKEN_BURN, new TokenBurnTranslator());
                    put(TOKEN_MINT, new TokenMintTranslator());
                    put(UTIL_PRNG, new UtilPrngTranslator());
                }
            };

    /**
     * Scans a block for genesis information and returns true if found.
     * @param block the block to scan
     * @return true if genesis information was found
     */
    public boolean scanBlockForGenesis(@NonNull final Block block) {
        return baseTranslator.scanMaybeGenesisBlock(block);
    }

    /**
     * Translates the given {@link BlockTransactionalUnit} into a list of {@link SingleTransactionRecord}s.
     * @param unit the unit to translate
     * @return the translated records
     */
    public List<SingleTransactionRecord> translate(@NonNull final BlockTransactionalUnit unit) {
        requireNonNull(unit);
        baseTranslator.prepareForUnit(unit);
        final List<StateChange> remainingStateChanges = new LinkedList<>(unit.stateChanges());
        final List<SingleTransactionRecord> translatedRecords = new ArrayList<>();
        for (final var blockTransactionParts : unit.blockTransactionParts()) {
            final var translator = translators.get(blockTransactionParts.functionality());
            if (translator == null) {
                log.warn("No translator found for functionality {}, skipping", blockTransactionParts.functionality());
            } else {
                final var translation =
                        translator.translate(blockTransactionParts, baseTranslator, remainingStateChanges);
                translatedRecords.add(translation);
            }
        }
        return translatedRecords;
    }
}
