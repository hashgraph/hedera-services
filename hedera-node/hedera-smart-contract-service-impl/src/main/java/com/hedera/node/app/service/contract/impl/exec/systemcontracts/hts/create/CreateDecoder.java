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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateSyntheticTxnFactory.createToken;
import static com.hedera.node.app.service.contract.impl.exec.utils.IdUtils.asContract;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.utils.KeyValueWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenExpiryWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenKeyWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
        final TokenCreateWrapper tokenCreateWrapper =
                getTokenCreateWrapperFungible(call.get(0), true, call.get(1), call.get(2), addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    private TransactionBody bodyOf(@NonNull final TokenCreateTransactionBody.Builder tokenCreate) {
        return TransactionBody.newBuilder().tokenCreation(tokenCreate).build();
    }

    private static TokenCreateWrapper getTokenCreateWrapperFungible(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final long initSupply,
            final int decimals,
            @NonNull final AddressIdConverter addressIdConverter) {

        final var tokenName = (String) tokenCreateStruct.get(0);
        final var tokenSymbol = (String) tokenCreateStruct.get(1);
        final var tokenTreasury = addressIdConverter.convert(tokenCreateStruct.get(2));
        final var memo = (String) tokenCreateStruct.get(3);
        final var isSupplyTypeFinite = (Boolean) tokenCreateStruct.get(4);
        final var maxSupply = (long) tokenCreateStruct.get(5);
        final var isFreezeDefault = (Boolean) tokenCreateStruct.get(6);
        final var tokenKeys = decodeTokenKeys(tokenCreateStruct.get(7), addressIdConverter);
        final var tokenExpiry = decodeTokenExpiry(tokenCreateStruct.get(8), addressIdConverter);

        return new TokenCreateWrapper(
                isFungible,
                tokenName,
                tokenSymbol,
                tokenTreasury.accountNum() != 0 ? tokenTreasury : null,
                memo,
                isSupplyTypeFinite,
                initSupply,
                decimals,
                maxSupply,
                isFreezeDefault,
                tokenKeys,
                tokenExpiry);
    }

    private static List<TokenKeyWrapper> decodeTokenKeys(
            @NonNull final Tuple[] tokenKeysTuples, @NonNull final AddressIdConverter addressIdConverter) {
        final List<TokenKeyWrapper> tokenKeys = new ArrayList<>(tokenKeysTuples.length);
        for (final var tokenKeyTuple : tokenKeysTuples) {
            final var keyType = ((BigInteger) tokenKeyTuple.get(0)).intValue();
            final Tuple keyValueTuple = tokenKeyTuple.get(1);
            final var inheritAccountKey = (Boolean) keyValueTuple.get(0);
            final var contractId = asContract(addressIdConverter.convert(keyValueTuple.get(1)));
            final var ed25519 = (byte[]) keyValueTuple.get(2);
            final var ecdsaSecp256K1 = (byte[]) keyValueTuple.get(3);
            final var delegatableContractId = asContract(addressIdConverter.convert(keyValueTuple.get(4)));

            tokenKeys.add(new TokenKeyWrapper(
                    keyType,
                    new KeyValueWrapper(
                            inheritAccountKey,
                            contractId.contractNum() != 0 ? contractId : null,
                            ed25519,
                            ecdsaSecp256K1,
                            delegatableContractId.contractNum() != 0 ? delegatableContractId : null)));
        }

        return tokenKeys;
    }

    private static TokenExpiryWrapper decodeTokenExpiry(
            @NonNull final Tuple expiryTuple, @NonNull final AddressIdConverter addressIdConverter) {
        final var second = (long) expiryTuple.get(0);
        final var autoRenewAccount = addressIdConverter.convert(expiryTuple.get(1));
        final var autoRenewPeriod =
                Duration.newBuilder().seconds(expiryTuple.get(2)).build();
        return new TokenExpiryWrapper(
                second, autoRenewAccount.accountNum() == 0 ? null : autoRenewAccount, autoRenewPeriod);
    }
}
