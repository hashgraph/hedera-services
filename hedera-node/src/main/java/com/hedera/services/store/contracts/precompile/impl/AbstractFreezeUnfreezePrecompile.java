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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Objects;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/* --- Constructor functional interfaces for mocking --- */
public abstract class AbstractFreezeUnfreezePrecompile extends AbstractWritePrecompile {

    private final boolean isFreeze;
    protected final ContractAliases aliases;
    protected final EvmSigsVerifier sigsVerifier;
    protected TokenFreezeUnfreezeWrapper freezeUnfreezeOp;

    protected AbstractFreezeUnfreezePrecompile(
            WorldLedgers ledgers,
            DecodingFacade decoder,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffects,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils,
            boolean isFreeze) {
        super(
                ledgers,
                decoder,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
        this.isFreeze = isFreeze;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(freezeUnfreezeOp);

        /* --- Check required signatures --- */
        final var tokenId = Id.fromGrpcToken(freezeUnfreezeOp.token());
        final var accountId = Id.fromGrpcAccount(freezeUnfreezeOp.account());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveFreezeKey,
                        ledgers,
                        aliases);
        validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        final var tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());

        final var freezingLogic =
                isFreeze
                        ? infrastructureFactory.newFreezeLogic(accountStore, tokenStore)
                        : infrastructureFactory.newUnFreezeLogic(accountStore, tokenStore);
        final var validity = freezingLogic.validate(transactionBody.build());
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        freezingLogic.doFreezeUnfreeze(tokenId, accountId);
    }
}
