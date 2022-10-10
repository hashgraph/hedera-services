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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractGrantRevokeKycPrecompile extends AbstractWritePrecompile {
    protected final ContractAliases aliases;
    protected final EvmSigsVerifier sigsVerifier;
    protected GrantRevokeKycWrapper grantRevokeOp;
    protected Id tokenId;
    protected Id accountId;
    protected AccountStore accountStore;
    protected TypedTokenStore tokenStore;

    protected AbstractGrantRevokeKycPrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffects,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
    }

    public void initialise(MessageFrame frame) {
        Objects.requireNonNull(grantRevokeOp);

        /* --- Check required signatures --- */
        tokenId = Id.fromGrpcToken(grantRevokeOp.token());
        accountId = Id.fromGrpcAccount(grantRevokeOp.account());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveKycKey,
                        ledgers,
                        aliases);
        validateTrue(hasRequiredSigs, INVALID_SIGNATURE);

        /* --- Build the necessary infrastructure to execute the transaction --- */
        accountStore = infrastructureFactory.newAccountStore(ledgers.accounts());
        tokenStore =
                infrastructureFactory.newTokenStore(
                        accountStore,
                        sideEffects,
                        ledgers.tokens(),
                        ledgers.nfts(),
                        ledgers.tokenRels());
    }
}
