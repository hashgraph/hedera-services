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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asNumericContractId;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.utils.KeyValueWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenExpiryWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenKeyWrapper;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateDecoder {

    // below values correspond to  tuples' indexes
    private static final int TOKEN_ADDRESS = 0;
    private static final int HEDERA_TOKEN = 1;

    private static final int EXPIRY = 1;
    private static final int TOKEN_KEYS = 1;

    private static final int KEY_TYPE = 0;
    private static final int KEY_VALUE = 1;

    private static final int INHERIT_ACCOUNT_KEY = 0;
    private static final int CONTRACT_ID = 1;
    private static final int ED25519 = 2;
    private static final int ECDSA_SECP_256K1 = 3;
    private static final int DELEGATABLE_CONTRACT_ID = 4;

    @Inject
    public UpdateDecoder() {}

    /**
     * Decodes a call to {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION} into a synthetic {@link TransactionBody}.
     *
     * @param attempt the attempt
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTokenUpdateV1(@NonNull final HtsCallAttempt attempt) {
        final var call = UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION.decodeCall(
                attempt.input().toArrayUnsafe());
        return decodeTokenUpdate(call, attempt.addressIdConverter());
    }

    /**
     * Decodes a call to {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V2} into a synthetic {@link TransactionBody}.
     *
     * @param attempt the attempt
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTokenUpdateV2(@NonNull final HtsCallAttempt attempt) {
        final var call = UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2.decodeCall(
                attempt.input().toArrayUnsafe());
        return decodeTokenUpdate(call, attempt.addressIdConverter());
    }

    /**
     * Decodes a call to {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V3} into a synthetic {@link TransactionBody}.
     *
     * @param attempt the attempt
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTokenUpdateV3(@NonNull final HtsCallAttempt attempt) {
        final var call = UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3.decodeCall(
                attempt.input().toArrayUnsafe());
        return decodeTokenUpdate(call, attempt.addressIdConverter());
    }

    /**
     * Decodes a call to {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V1} into a synthetic {@link TransactionBody}.
     *
     * @param attempt the attempt
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTokenUpdateExpiryV1(@NonNull final HtsCallAttempt attempt) {
        final var call = UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V1.decodeCall(
                attempt.input().toArrayUnsafe());
        return decodeTokenUpdateExpiry(call, attempt.addressIdConverter());
    }

    /**
     * Decodes a call to {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V2} into a synthetic {@link TransactionBody}.
     *
     * @param attempt the attempt
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTokenUpdateExpiryV2(@NonNull final HtsCallAttempt attempt) {
        final var call = UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V2.decodeCall(
                attempt.input().toArrayUnsafe());
        return decodeTokenUpdateExpiry(call, attempt.addressIdConverter());
    }

    private TransactionBody decodeTokenUpdate(
            @NonNull final Tuple call, @NonNull final AddressIdConverter addressIdConverter) {
        final var tokenId = ConversionUtils.asTokenId(call.get(TOKEN_ADDRESS));
        final var hederaToken = (Tuple) call.get(HEDERA_TOKEN);

        final var tokenName = (String) hederaToken.get(0);
        final var tokenSymbol = (String) hederaToken.get(1);
        final var tokenTreasury = addressIdConverter.convert(hederaToken.get(2));
        final var memo = (String) hederaToken.get(3);
        final List<TokenKeyWrapper> tokenKeys = decodeTokenKeys(hederaToken.get(7), addressIdConverter);
        final var tokenExpiry = decodeTokenExpiry(hederaToken.get(8), addressIdConverter);

        // Build the transaction body
        final var txnBodyBuilder = TokenUpdateTransactionBody.newBuilder();
        txnBodyBuilder.token(tokenId);

        if (tokenName != null) {
            txnBodyBuilder.name(tokenName);
        }
        if (tokenSymbol != null) {
            txnBodyBuilder.symbol(tokenSymbol);
        }
        if (memo != null) {
            txnBodyBuilder.memo(memo);
        }

        txnBodyBuilder.treasury(tokenTreasury);

        if (tokenExpiry.second() != 0) {
            txnBodyBuilder.expiry(
                    Timestamp.newBuilder().seconds(tokenExpiry.second()).build());
        }
        if (tokenExpiry.autoRenewAccount() != null) {
            txnBodyBuilder.autoRenewAccount(tokenExpiry.autoRenewAccount());
        }
        if (tokenExpiry.autoRenewPeriod() != null
                && tokenExpiry.autoRenewPeriod().seconds() != 0) {
            txnBodyBuilder.autoRenewPeriod(tokenExpiry.autoRenewPeriod());
        }

        return checkTokenKeysTypeAndBuild(tokenKeys, txnBodyBuilder);
    }

    public TransactionBody decodeTokenUpdateKeys(@NonNull final HtsCallAttempt attempt) {
        final var call = UpdateKeysTranslator.TOKEN_UPDATE_KEYS_FUNCTION.decodeCall(
                attempt.input().toArrayUnsafe());

        final var tokenId = ConversionUtils.asTokenId(call.get(TOKEN_ADDRESS));
        final var tokenKeys = decodeTokenKeys(call.get(TOKEN_KEYS), attempt.addressIdConverter());

        // Build the transaction body
        final var txnBodyBuilder = TokenUpdateTransactionBody.newBuilder();
        txnBodyBuilder.token(tokenId);

        return checkTokenKeysTypeAndBuild(tokenKeys, txnBodyBuilder);
    }

    private TransactionBody checkTokenKeysTypeAndBuild(
            final List<TokenKeyWrapper> tokenKeys, final TokenUpdateTransactionBody.Builder builder) {
        tokenKeys.forEach(tokenKeyWrapper -> {
            final var key = tokenKeyWrapper.key().asGrpc();
            if (tokenKeyWrapper.isUsedForAdminKey()) {
                builder.adminKey(key);
            }
            if (tokenKeyWrapper.isUsedForKycKey()) {
                builder.kycKey(key);
            }
            if (tokenKeyWrapper.isUsedForFreezeKey()) {
                builder.freezeKey(key);
            }
            if (tokenKeyWrapper.isUsedForWipeKey()) {
                builder.wipeKey(key);
            }
            if (tokenKeyWrapper.isUsedForSupplyKey()) {
                builder.supplyKey(key);
            }
            if (tokenKeyWrapper.isUsedForFeeScheduleKey()) {
                builder.feeScheduleKey(key);
            }
            if (tokenKeyWrapper.isUsedForPauseKey()) {
                builder.pauseKey(key);
            }
        });

        return TransactionBody.newBuilder().tokenUpdate(builder).build();
    }

    private TransactionBody decodeTokenUpdateExpiry(
            @NonNull final Tuple call, @NonNull final AddressIdConverter addressIdConverter) {
        final var tokenId = (Address) call.get(TOKEN_ADDRESS);
        final var expiryTuple = (Tuple) call.get(EXPIRY);
        final var txnBodyBuilder = TokenUpdateTransactionBody.newBuilder();

        txnBodyBuilder.token(ConversionUtils.asTokenId(tokenId));
        final var tokenExpiry = decodeTokenExpiry(expiryTuple, addressIdConverter);

        if (tokenExpiry.second() != 0) {
            txnBodyBuilder.expiry(
                    Timestamp.newBuilder().seconds(tokenExpiry.second()).build());
        }
        if (tokenExpiry.autoRenewAccount() != null) {
            txnBodyBuilder.autoRenewAccount(tokenExpiry.autoRenewAccount());
        }
        if (tokenExpiry.autoRenewPeriod() != null
                && tokenExpiry.autoRenewPeriod().seconds() != 0) {
            txnBodyBuilder.autoRenewPeriod(tokenExpiry.autoRenewPeriod());
        }

        return TransactionBody.newBuilder().tokenUpdate(txnBodyBuilder).build();
    }

    private static List<TokenKeyWrapper> decodeTokenKeys(
            @NonNull final Tuple[] tokenKeysTuples, @NonNull final AddressIdConverter addressIdConverter) {
        final List<TokenKeyWrapper> tokenKeys = new ArrayList<>(tokenKeysTuples.length);
        for (final var tokenKeyTuple : tokenKeysTuples) {
            final var keyType = ((BigInteger) tokenKeyTuple.get(KEY_TYPE)).intValue();
            final Tuple keyValueTuple = tokenKeyTuple.get(KEY_VALUE);
            final var inheritAccountKey = (Boolean) keyValueTuple.get(INHERIT_ACCOUNT_KEY);
            final var contractId = asNumericContractId(addressIdConverter.convert(keyValueTuple.get(CONTRACT_ID)));
            final var ed25519 = (byte[]) keyValueTuple.get(ED25519);
            final var ecdsaSecp256K1 = (byte[]) keyValueTuple.get(ECDSA_SECP_256K1);
            final var delegatableContractId =
                    asNumericContractId(addressIdConverter.convert(keyValueTuple.get(DELEGATABLE_CONTRACT_ID)));

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
