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
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateWrapper;
import com.hedera.services.store.contracts.precompile.utils.KeyActivationUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UPDATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class TokenUpdatePrecompile extends AbstractTokenUpdatePrecompile {
    private TokenUpdateWrapper updateOp;

    public TokenUpdatePrecompile(
            WorldLedgers ledgers,
            ContractAliases aliases,
            DecodingFacade decoder,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffectsTracker,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils precompilePricingUtils) {
        super(
                ledgers,
                aliases,
                decoder,
                sigsVerifier,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                precompilePricingUtils);
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        updateOp = decoder.decodeUpdateTokenInfo(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createTokenUpdate(updateOp);
        return transactionBody;
    }

    @Override
    public void run(MessageFrame frame) {
        Objects.requireNonNull(updateOp);
        var hederaTokenStore = initializeHederaTokenStore();
        /* --- Check required signatures --- */
        final var tokenId = Id.fromGrpcToken(updateOp.tokenID());
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
        updateLogic.updateToken(
                transactionBody.getTokenUpdate(), frame.getBlockValues().getTimestamp());
    }
}
