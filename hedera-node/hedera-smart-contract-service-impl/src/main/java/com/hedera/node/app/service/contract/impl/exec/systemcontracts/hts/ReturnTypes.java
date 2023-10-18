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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.FixedFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

/**
 * Literal representations of output types used by HTS system contract functions.
 */
public class ReturnTypes {
    private ReturnTypes() {
        throw new UnsupportedOperationException("Utility class");
    }

    // When no value is set for AccountID, ContractID or TokenId the return value is set to 0.
    public static final AccountID ZERO_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(0).build();
    public static final ContractID ZERO_CONTRACT_ID =
            ContractID.newBuilder().contractNum(0).build();
    public static final TokenID ZERO_TOKEN_ID = TokenID.newBuilder().tokenNum(0).build();
    public static final Fraction ZERO_FRACTION = new Fraction(0, 1);
    public static final FixedFee ZERO_FIXED_FEE = new FixedFee(0, null);

    private static final String RESPONSE_STATUS_AT_BEGINNING = "(int32,";

    public static final String INT = "(int)";
    public static final String INT_64 = "(int64)";
    public static final String BYTE = "(uint8)";
    public static final String BOOL = "(bool)";
    public static final String STRING = "(string)";
    public static final String ADDRESS = "(address)";
    public static final String RESPONSE_CODE_BOOL = "(int32,bool)";
    public static final String RESPONSE_CODE_INT32 = "(int32,int32)";
    public static final String RESPONSE_CODE_UINT256 = "(int64,uint256)";
    public static final String UINT256 = "(uint256)";
    public static final String RESPONSE_CODE_EXPIRY = "(int32,"
            // Expiry
            + "(uint32,address,uint32)" // second, autoRenewAccount, autoRenewPeriod
            + ")";
    public static final String RESPONSE_CODE_TOKEN_KEY = "(int32,"
            // KeyValue
            + "(bool,address,bytes,bytes,address)" // inheritedAccountKey, contractId, ed25519, ECDSA_secp256k1,
            // delegatableContractId
            + ")";
    // spotless:off
    public static final String RESPONSE_CODE_TOKEN_INFO = "(int32,"
            // TokenInfo
            + "("
                // HederaToken
                + "("
                    + "string,string,address,string,bool,int64,bool," // name, symbol, treasury, memo, tokenSupplyType, maxSupply, freezeDefault
                    // TokenKey array
                    + "("
                        + "uint256," // keyType
                        // KeyValue
                        + "(bool,address,bytes,bytes,address)" // inheritedAccountKey, contractId, ed25519, ECDSA_secp256k1, delegatableContractId
                    + ")[],"
                    // Expiry
                    + "(uint32,address,uint32)" // second, autoRenewAccount, autoRenewPeriod
                + ")"

                + ",int64,bool,bool,bool," // totalSupply, deleted, defaultKycStatus, pauseStatus

                // FixedFee array
                + "(uint32,address,bool,bool,address)[]," // amount, tokenId, useHbarsForPayment, useCurrentTokenForPayment, feeCollector
                // FractionalFee array
                + "(uint32,uint32,uint32,uint32,bool,address)[]," // numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector
                // RoyaltyFee array
                + "(uint32,uint32,uint32,address,bool,address)[]" // numerator, denominator, amount, tokenId, useHbarsForPayment, feeCollector
                + ",string" // ledgerId
            + ")"
        + ")";
    public static final String RESPONSE_CODE_FUNGIBLE_TOKEN_INFO = "(int32,"
            // FungibleTokenInfo
            + "("
                // TokenInfo
                + "("
                    // HederaToken
                    + "("
                        + "string,string,address,string,bool,int64,bool," // name, symbol, treasury, memo, tokenSupplyType, maxSupply, freezeDefault
                        // TokenKey array
                        + "("
                            + "uint256," // keyType
                            // KeyValue
                            + "(bool,address,bytes,bytes,address)" // inheritedAccountKey, contractId, ed25519, ECDSA_secp256k1, delegatableContractId
                        + ")[],"
                        // Expiry
                        + "(uint32,address,uint32)" // second, autoRenewAccount, autoRenewPeriod
                    + ")"

                    + ",int64,bool,bool,bool," // totalSupply, deleted, defaultKycStatus, pauseStatus

                    // FixedFee array
                    + "(uint32,address,bool,bool,address)[]," // amount, tokenId, useHbarsForPayment, useCurrentTokenForPayment, feeCollector
                    // FractionalFee array
                    + "(uint32,uint32,uint32,uint32,bool,address)[]," // numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector
                    // RoyaltyFee array
                    + "(uint32,uint32,uint32,address,bool,address)[]" // numerator, denominator, amount, tokenId, useHbarsForPayment, feeCollector
                    + ",string" // ledgerId
                + "),"
                + "int32" // decimals
            + ")"
        + ")";
    public static final String RESPONSE_CODE_NON_FUNGIBLE_TOKEN_INFO = "(int32,"
            // NonFungibleTokenInfo
            + "("
                // TokenInfo
                + "("
                    // HederaToken
                    + "("
                        + "string,string,address,string,bool,int64,bool," // name, symbol, treasury, memo, tokenSupplyType, maxSupply, freezeDefault
                        // TokenKey array
                        + "("
                            + "uint256," // keyType
                            // KeyValue
                            + "(bool,address,bytes,bytes,address)" // inheritedAccountKey, contractId, ed25519, ECDSA_secp256k1, delegatableContractId
                        + ")[],"
                        // Expiry
                        + "(uint32,address,uint32)" // second, autoRenewAccount, autoRenewPeriod
                    + ")"

                    + ",int64,bool,bool,bool," // totalSupply, deleted, defaultKycStatus, pauseStatus

                    // FixedFee array
                    + "(uint32,address,bool,bool,address)[]," // amount, tokenId, useHbarsForPayment, useCurrentTokenForPayment, feeCollector
                    // FractionalFee array
                    + "(uint32,uint32,uint32,uint32,bool,address)[]," // numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector
                    // RoyaltyFee array
                    + "(uint32,uint32,uint32,address,bool,address)[]" // numerator, denominator, amount, tokenId, useHbarsForPayment, feeCollector
                    + ",string" // ledgerId
                + "),"
                + "int64,address,int64,bytes,address" // serialNumber, ownerId, creationTime, metadata, spenderId
            + ")"
        + ")";

    public static final String RESPONSE_CODE_CUSTOM_FEES = "(int32,"
            // FixedFee array
            + "(uint32,address,bool,bool,address)[]," // amount, tokenId, useHbarsForPayment, useCurrentTokenForPayment, feeCollector
            // FractionalFee array
            + "(uint32,uint32,uint32,uint32,bool,address)[]," // numerator, denominator, minimumAmount, maximumAmount, netOfTransfers, feeCollector
            // RoyaltyFee array
            + "(uint32,uint32,uint32,address,bool,address)[]" // numerator, denominator, amount, tokenId, useHbarsForPayment, feeCollector
        + ")";
    // spotless:on

    private static final TupleType RC_ENCODER = TupleType.parse(INT_64);

    /**
     * Encodes the given {@code status} as a return value for a classic transfer call.
     *
     * @param status the status to encode
     * @return the encoded status
     */
    public static ByteBuffer encodedRc(@NonNull final ResponseCodeEnum status) {
        return RC_ENCODER.encodeElements((long) status.protoOrdinal());
    }
}
