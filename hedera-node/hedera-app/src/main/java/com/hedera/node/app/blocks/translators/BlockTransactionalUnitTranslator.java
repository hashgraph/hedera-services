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

package com.hedera.node.app.blocks.translators;

import static com.hedera.node.app.blocks.translators.impl.AirdropRemovalTranslator.AIRDROP_REMOVAL_TRANSLATOR;
import static com.hedera.node.app.blocks.translators.impl.NoExplicitSideEffectsTranslator.NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.blocks.translators.impl.ContractCallTranslator;
import com.hedera.node.app.blocks.translators.impl.ContractCreateTranslator;
import com.hedera.node.app.blocks.translators.impl.ContractDeleteTranslator;
import com.hedera.node.app.blocks.translators.impl.ContractUpdateTranslator;
import com.hedera.node.app.blocks.translators.impl.CryptoCreateTranslator;
import com.hedera.node.app.blocks.translators.impl.CryptoTransferTranslator;
import com.hedera.node.app.blocks.translators.impl.CryptoUpdateTranslator;
import com.hedera.node.app.blocks.translators.impl.EthereumTransactionTranslator;
import com.hedera.node.app.blocks.translators.impl.FileCreateTranslator;
import com.hedera.node.app.blocks.translators.impl.NodeCreateTranslator;
import com.hedera.node.app.blocks.translators.impl.ScheduleCreateTranslator;
import com.hedera.node.app.blocks.translators.impl.ScheduleDeleteTranslator;
import com.hedera.node.app.blocks.translators.impl.ScheduleSignTranslator;
import com.hedera.node.app.blocks.translators.impl.SubmitMessageTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenAirdropTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenAssociateTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenBurnTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenCreateTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenDissociateTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenMintTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenUpdateTranslator;
import com.hedera.node.app.blocks.translators.impl.TokenWipeTranslator;
import com.hedera.node.app.blocks.translators.impl.TopicCreateTranslator;
import com.hedera.node.app.blocks.translators.impl.UtilPrngTranslator;
import com.hedera.node.app.state.SingleTransactionRecord;
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
    private static final Logger LOGGER = LogManager.getLogger(BlockTransactionalUnitTranslator.class);

    /**
     * The base translator used to create the {@link SingleTransactionRecord}s.
     */
    private final BaseTranslator baseTranslator = new BaseTranslator();
    /**
     * The translators used to translate the block transaction parts for a logical HAPI transaction.
     */
    private final Map<HederaFunctionality, BlockTransactionPartsTranslator> translators =
            new EnumMap<>(HederaFunctionality.class);

    public BlockTransactionalUnitTranslator() {
        translators.put(HederaFunctionality.CONSENSUS_CREATE_TOPIC, new TopicCreateTranslator());
        translators.put(HederaFunctionality.CONSENSUS_DELETE_TOPIC, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE, new SubmitMessageTranslator());
        translators.put(HederaFunctionality.CONSENSUS_UPDATE_TOPIC, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.CONTRACT_CALL, new ContractCallTranslator());
        translators.put(HederaFunctionality.CONTRACT_CREATE, new ContractCreateTranslator());
        translators.put(HederaFunctionality.CONTRACT_DELETE, new ContractDeleteTranslator());
        translators.put(HederaFunctionality.CONTRACT_UPDATE, new ContractUpdateTranslator());
        translators.put(HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.CRYPTO_DELETE_ALLOWANCE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.CRYPTO_CREATE, new CryptoCreateTranslator());
        translators.put(HederaFunctionality.CRYPTO_DELETE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.CRYPTO_TRANSFER, new CryptoTransferTranslator());
        translators.put(HederaFunctionality.CRYPTO_UPDATE, new CryptoUpdateTranslator());
        translators.put(HederaFunctionality.ETHEREUM_TRANSACTION, new EthereumTransactionTranslator());
        translators.put(HederaFunctionality.FILE_APPEND, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.FILE_CREATE, new FileCreateTranslator());
        translators.put(HederaFunctionality.FILE_DELETE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.FILE_UPDATE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.FREEZE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.NODE_CREATE, new NodeCreateTranslator());
        translators.put(HederaFunctionality.NODE_DELETE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.NODE_UPDATE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.NODE_STAKE_UPDATE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.SCHEDULE_CREATE, new ScheduleCreateTranslator());
        translators.put(HederaFunctionality.SCHEDULE_DELETE, new ScheduleDeleteTranslator());
        translators.put(HederaFunctionality.SCHEDULE_SIGN, new ScheduleSignTranslator());
        translators.put(HederaFunctionality.SYSTEM_DELETE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.SYSTEM_UNDELETE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_AIRDROP, new TokenAirdropTranslator());
        translators.put(HederaFunctionality.TOKEN_CLAIM_AIRDROP, AIRDROP_REMOVAL_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_CANCEL_AIRDROP, AIRDROP_REMOVAL_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_REJECT, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_ACCOUNT_WIPE, new TokenWipeTranslator());
        translators.put(HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT, new TokenAssociateTranslator());
        translators.put(HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT, new TokenDissociateTranslator());
        translators.put(HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_BURN, new TokenBurnTranslator());
        translators.put(HederaFunctionality.TOKEN_CREATE, new TokenCreateTranslator());
        translators.put(HederaFunctionality.TOKEN_DELETE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_FREEZE_ACCOUNT, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_UPDATE_NFTS, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_MINT, new TokenMintTranslator());
        translators.put(HederaFunctionality.TOKEN_PAUSE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_UNPAUSE, NO_EXPLICIT_SIDE_EFFECTS_TRANSLATOR);
        translators.put(HederaFunctionality.TOKEN_UPDATE, new TokenUpdateTranslator());
        translators.put(HederaFunctionality.UTIL_PRNG, new UtilPrngTranslator());
    }

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
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.warn(
                            "No translator found for functionality {}, skipping",
                            blockTransactionParts.functionality());
                }
            } else {
                final var translation =
                        translator.translate(blockTransactionParts, baseTranslator, remainingStateChanges);
                translatedRecords.add(translation);
            }
        }
        return translatedRecords;
    }
}
