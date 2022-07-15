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
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PAUSE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class PausePrecompile extends AbstractWritePrecompile {
    protected PauseWrapper pauseOp;
    protected final ContractAliases aliases;
    protected final EvmSigsVerifier sigsVerifier;

    public PausePrecompile(
            WorldLedgers ledgers,
            DecodingFacade decoder,
            final ContractAliases aliases,
            final EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffects,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils pricingUtils) {
        super(
                ledgers,
                decoder,
                sideEffects,
                syntheticTxnFactory,
                infrastructureFactory,
                pricingUtils);
        this.aliases = aliases;
        this.sigsVerifier = sigsVerifier;
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        pauseOp = decoder.decodePause(input);
        transactionBody = syntheticTxnFactory.createPause(pauseOp);
        return transactionBody;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        Objects.requireNonNull(pauseOp);
        return pricingUtils.getMinimumPriceInTinybars(PAUSE, consensusTime);
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(pauseOp);

        /* --- Check required signatures --- */
        final var tokenId = Id.fromGrpcToken(pauseOp.token());
        ledgers.tokens().get(pauseOp.token(), TokenProperty.PAUSE_KEY);
                final var hasRequiredSigs = KeyActivationUtils.validateKey(
                        frame,
                        tokenId.asEvmAddress(),
                        sigsVerifier::hasActivePauseKey,
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
        final var pauseLogic = infrastructureFactory.newPauseLogic(tokenStore);
        final var validity = pauseLogic.validateSyntax(transactionBody.build());
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        pauseLogic.pause(tokenId);
    }
}
