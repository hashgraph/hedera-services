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
import static com.hedera.services.store.contracts.precompile.impl.AbstractTokenUpdatePrecompile.UpdateType.UPDATE_TOKEN_INFO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenUpdateWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

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
        validateTrue(updateOp.tokenID() != null, INVALID_TOKEN_ID);
        tokenId = Id.fromGrpcToken(updateOp.tokenID());
        type = UPDATE_TOKEN_INFO;
        super.run(frame);
    }
}
