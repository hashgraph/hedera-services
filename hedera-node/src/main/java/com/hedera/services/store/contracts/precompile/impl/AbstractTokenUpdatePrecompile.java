/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UPDATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractTokenUpdatePrecompile extends AbstractWritePrecompile {
    protected final ContractAliases aliases;
    protected final EvmSigsVerifier sigsVerifier;
    protected UpdateType type;
    protected Id tokenId;

    protected AbstractTokenUpdatePrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            DecodingFacade decoder,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffectsTracker,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils) {
        super(
                ledgers,
                decoder,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return pricingUtils.getMinimumPriceInTinybars(UPDATE, consensusTime);
    }

    @Override
    public void run(MessageFrame frame) {

        var hederaTokenStore = initializeHederaTokenStore();

        /* --- Check required signatures --- */
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveAdminKey,
                        ledgers,
                        aliases);
        validateTrue(hasRequiredSigs, INVALID_SIGNATURE);
        hederaTokenStore.setAccountsLedger(ledgers.accounts());
        /* --- Build the necessary infrastructure to execute the transaction --- */
        TokenUpdateLogic updateLogic =
                infrastructureFactory.newTokenUpdateLogic(hederaTokenStore, ledgers, sideEffects);

        final var validity = updateLogic.validate(transactionBody.build());
        validateTrue(validity == OK, validity);
        /* --- Execute the transaction and capture its results --- */
        switch (type) {
            case UPDATE_TOKEN_INFO -> updateLogic.updateToken(
                    transactionBody.getTokenUpdate(), frame.getBlockValues().getTimestamp());
            case UPDATE_TOKEN_KEYS -> updateLogic.updateTokenKeys(
                    transactionBody.getTokenUpdate(), frame.getBlockValues().getTimestamp());
            case UPDATE_TOKEN_EXPIRY -> updateLogic.updateTokenExpiryInfo(
                    transactionBody.getTokenUpdate());
        }
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
