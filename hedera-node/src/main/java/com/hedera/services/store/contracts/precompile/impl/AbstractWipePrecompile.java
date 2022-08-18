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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractWipePrecompile extends AbstractWritePrecompile {

    protected final ContractAliases aliases;
    protected final EvmSigsVerifier sigsVerifier;
    protected WipeWrapper wipeOp;
    private static final List<Long> NO_SERIAL_NOS = Collections.emptyList();

    protected AbstractWipePrecompile(
            WorldLedgers ledgers,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffects,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils) {
        super(ledgers, sideEffects, syntheticTxnFactory, infrastructureFactory, pricingUtils);
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(wipeOp);

        /* --- Check required signatures --- */
        final var tokenId = Id.fromGrpcToken(wipeOp.token());
        final var accountId = Id.fromGrpcAccount(wipeOp.account());
        final var hasRequiredSigs =
                KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActiveWipeKey,
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
        final var wipeLogic = infrastructureFactory.newWipeLogic(accountStore, tokenStore);
        final var validity = wipeLogic.validateSyntax(transactionBody.build());
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        if (wipeOp.type() == NON_FUNGIBLE_UNIQUE) {
            final var targetSerialNumbers = wipeOp.serialNumbers();
            wipeLogic.wipe(tokenId, accountId, 0, targetSerialNumbers);
        } else {
            wipeLogic.wipe(tokenId, accountId, wipeOp.amount(), NO_SERIAL_NOS);
        }
    }
}
