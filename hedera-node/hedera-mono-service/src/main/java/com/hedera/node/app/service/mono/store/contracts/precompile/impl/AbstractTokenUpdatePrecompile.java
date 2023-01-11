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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UPDATE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.sigs.KeyValidator;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.sigs.LegacyKeyValidator;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractTokenUpdatePrecompile extends AbstractWritePrecompile {
    protected final KeyValidator keyValidator;
    protected final LegacyKeyValidator legacyKeyValidator;
    protected final ContractAliases aliases;
    protected final EvmSigsVerifier sigsVerifier;
    protected UpdateType type;
    protected Id tokenId;

    protected AbstractTokenUpdatePrecompile(
            final KeyValidator keyValidator,
            final LegacyKeyValidator legacyKeyValidator,
            final WorldLedgers ledgers,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            final SideEffectsTracker sideEffectsTracker,
            final SyntheticTxnFactory syntheticTxnFactory,
            final InfrastructureFactory infrastructureFactory,
            final PrecompilePricingUtils pricingUtils) {
        super(
                ledgers,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
        this.aliases = aliases;
        this.keyValidator = keyValidator;
        this.legacyKeyValidator = legacyKeyValidator;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        return pricingUtils.getMinimumPriceInTinybars(UPDATE, consensusTime);
    }

    @Override
    public void run(final MessageFrame frame) {
        final var txn = transactionBody.build();
        final var updateOp = txn.getTokenUpdate();

        final var hederaTokenStore = initializeHederaTokenStore();

        /* --- Check required signatures --- */
        final var hasAdminSig =
                keyValidator.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveAdminKey,
                        ledgers,
                        aliases);
        validateTrue(hasAdminSig, INVALID_SIGNATURE);
        if (updateOp.hasTreasury()) {
            validateTreasurySig(frame, updateOp);
        }
        if (updateOp.hasAutoRenewAccount()) {
            validateAutoRenewSig(frame, updateOp);
        }
        hederaTokenStore.setAccountsLedger(ledgers.accounts());
        /* --- Build the necessary infrastructure to execute the transaction --- */
        final TokenUpdateLogic updateLogic =
                infrastructureFactory.newTokenUpdateLogic(hederaTokenStore, ledgers, sideEffects);

        final var validity = updateLogic.validate(transactionBody.build());
        validateTrue(validity == OK, validity);
        /* --- Execute the transaction and capture its results --- */
        switch (type) {
            case UPDATE_TOKEN_INFO -> updateLogic.updateToken(
                    updateOp, frame.getBlockValues().getTimestamp());
            case UPDATE_TOKEN_KEYS -> updateLogic.updateTokenKeys(
                    updateOp, frame.getBlockValues().getTimestamp());
            case UPDATE_TOKEN_EXPIRY -> updateLogic.updateTokenExpiryInfo(updateOp);
        }
    }

    private void validateTreasurySig(
            final MessageFrame frame, final TokenUpdateTransactionBody updateOp) {
        validateLegacyAccountSig(updateOp.getTreasury(), frame, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
    }

    private void validateAutoRenewSig(
            final MessageFrame frame, final TokenUpdateTransactionBody updateOp) {
        validateLegacyAccountSig(updateOp.getAutoRenewAccount(), frame, INVALID_AUTORENEW_ACCOUNT);
    }

    private void validateLegacyAccountSig(
            final AccountID id, final MessageFrame frame, final ResponseCodeEnum rcWhenMissing) {
        validateTrue(ledgers.accounts().exists(id), rcWhenMissing);
        final var hasSig =
                legacyKeyValidator.validateKey(
                        frame,
                        asTypedEvmAddress(id),
                        sigsVerifier::hasLegacyActiveKey,
                        ledgers,
                        aliases);
        validateTrue(hasSig, INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE);
    }

    private HederaTokenStore initializeHederaTokenStore() {
        return infrastructureFactory.newHederaTokenStore(
                sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
    }

    protected enum UpdateType {
        UPDATE_TOKEN_KEYS,
        UPDATE_TOKEN_INFO,
        UPDATE_TOKEN_EXPIRY
    }
}
