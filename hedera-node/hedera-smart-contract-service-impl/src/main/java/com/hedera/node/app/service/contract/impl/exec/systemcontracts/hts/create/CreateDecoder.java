/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CreateDecoder {

    @Inject
    public CreateDecoder() {
        // Dagger2
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_TOKEN} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleToken(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_TOKEN.decodeCall(encoded);
        final var hederaToken = (Tuple) call.get(0);
        final var txnBodyBuilder = TokenCreateTransactionBody.newBuilder();

        txnBodyBuilder.name(hederaToken.get(0));
        txnBodyBuilder.symbol(hederaToken.get(1));
        txnBodyBuilder.decimals(call.get(2));
        txnBodyBuilder.tokenType(TokenType.FUNGIBLE_COMMON);
        txnBodyBuilder.supplyType(hederaToken.get(4) ? TokenSupplyType.FINITE : TokenSupplyType.INFINITE);
        txnBodyBuilder.maxSupply(hederaToken.get(5));
        txnBodyBuilder.initialSupply(call.get(1));

        // checks for treasury
        if (hederaToken.get(2) != null) {
            txnBodyBuilder.treasury(addressIdConverter.convert(hederaToken.get(2)));
        }

        // @TODO iterate through TokenKeys and check for keyType

        txnBodyBuilder.freezeDefault(hederaToken.get(6));
        txnBodyBuilder.memo(hederaToken.get(3));

        // checks for expiry
        if (hederaToken.get(8) != null) {
            final var expiry = (Tuple) hederaToken.get(8);
            if (expiry.get(0) != null) {
                txnBodyBuilder.expiry(
                        Timestamp.newBuilder().seconds(expiry.get(0)).build());
            }
            if (expiry.get(1) != null) {
                txnBodyBuilder.autoRenewAccount(addressIdConverter.convert(expiry.get(1)));
            }
            if (expiry.get(2) != null) {
                txnBodyBuilder.autoRenewPeriod(
                        Duration.newBuilder().seconds(expiry.get(2)).build());
            }
        }

        return bodyOf(txnBodyBuilder);
    }

    private TransactionBody bodyOf(@NonNull final TokenCreateTransactionBody.Builder tokenCreate) {
        return TransactionBody.newBuilder().tokenCreation(tokenCreate).build();
    }
}
