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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.KeyValueWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetTokenKeyPrecompile extends AbstractReadOnlyPrecompile {
    private TokenProperty keyType;
    private final StateView stateView;

    public GetTokenKeyPrecompile(
            TokenID tokenId,
            SyntheticTxnFactory syntheticTxnFactory,
            WorldLedgers ledgers,
            EncodingFacade encoder,
            DecodingFacade decoder,
            PrecompilePricingUtils pricingUtils,
            StateView stateView) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
        this.stateView = stateView;
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final var getTokenKeyWrapper = decoder.decodeGetTokenKeys(input);
        tokenId = getTokenKeyWrapper.tokenID();
        keyType = getTokenKeyWrapper.tokenKeyType();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
        return encoder.encodeGetTokenKey(buildKeyValueWrapper());
    }

    private KeyValueWrapper buildKeyValueWrapper() {
        validateTrue(stateView.tokenExists(tokenId), ResponseCodeEnum.INVALID_TOKEN_ID);
        JKey key = (JKey) ledgers.tokens().get(tokenId, keyType);
        validateTrue(key != null, ResponseCodeEnum.KEY_NOT_PROVIDED);
        ContractID contractID = ContractID.getDefaultInstance();
        byte[] ed25519 = new byte[0];
        byte[] ecdsaSecp256k1 = new byte[0];
        ContractID delegatableContractID = ContractID.getDefaultInstance();
        if (key.hasContractID()) contractID = key.getContractIDKey().getContractID();
        if (key.hasEd25519Key()) ed25519 = key.getEd25519();
        if (key.hasECDSAsecp256k1Key()) ecdsaSecp256k1 = key.getECDSASecp256k1Key();
        if (key.hasDelegatableContractId())
            delegatableContractID = key.getDelegatableContractIdKey().getContractID();

        return new KeyValueWrapper(
                false, contractID, ed25519, ecdsaSecp256k1, delegatableContractID);
    }
}
