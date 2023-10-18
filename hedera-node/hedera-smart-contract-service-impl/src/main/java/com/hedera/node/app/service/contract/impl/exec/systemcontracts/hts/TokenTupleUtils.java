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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_FIXED_FEE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_FRACTION;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Utility class with methods for assembling {@code Tuple} containing token information
 */
public class TokenTupleUtils {
    private TokenTupleUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a tuple of the {@code Expiry} struct
     * {@see https://github.com/hashgraph/hedera-smart-contracts/blob/main/contracts/hts-precompile/IHederaTokenService.sol#L69 }
     * @param token the token to get the expiry for
     * @return Tuple encoding of the Expiry
     */
    @NonNull
    public static Tuple expiryTupleFor(@NonNull final Token token) {
        return Tuple.of(
                token.expirationSecond(),
                headlongAddressOf(token.autoRenewAccountIdOrElse(ZERO_ACCOUNT_ID)),
                token.autoRenewSeconds());
    }

    /**
     * Returns a tuple of the {@code KeyValue} struct
     * {@see https://github.com/hashgraph/hedera-smart-contracts/blob/main/contracts/hts-precompile/IHederaTokenService.sol#L92 }
     * @param key the key to get the tuple for
     * @return Tuple encoding of the KeyValue
     */
    @NonNull
    public static Tuple keyTupleFor(@NonNull final Key key) {
        return Tuple.of(
                false,
                headlongAddressOf(key.contractIDOrElse(ZERO_CONTRACT_ID)),
                key.ed25519OrElse(Bytes.EMPTY).toByteArray(),
                key.ecdsaSecp256k1OrElse(Bytes.EMPTY).toByteArray(),
                headlongAddressOf(key.delegatableContractIdOrElse(ZERO_CONTRACT_ID)));
    }

    /**
     * Returns a tuple containing the response code, fixedFees, fractionalFees and the royaltyFees for the token
     * @param token the token to get the fees for
     * @return Tuple encoding of the arrays of fixedFees, fractionalFees and royaltyFees
     */
    @NonNull
    public static Tuple feesTupleFor(final int responseCode, @NonNull final Token token) {
        final var fixedFees = fixedFeesTupleListFor(token);
        final var fractionalFees = fractionalFeesTupleListFor(token);
        final var royaltyFees = royaltyFeesTupleListFor(token);
        return Tuple.of(
                responseCode,
                fixedFeesTupleListFor(token).toArray(new Tuple[fixedFees.size()]),
                fractionalFeesTupleListFor(token).toArray(new Tuple[fractionalFees.size()]),
                royaltyFeesTupleListFor(token).toArray(new Tuple[royaltyFees.size()]));
    }

    /**
     * Returns a list of Tuples defined by the {@code FixedFee} structs
     * {@see https://github.com/hashgraph/hedera-smart-contracts/blob/main/contracts/hts-precompile/IHederaTokenService.sol#L236 }
     * @param token the token to get the fixed fees for
     * @return Tuple encoding of the FixedFee
     */
    @SuppressWarnings("DataFlowIssue")
    @NonNull
    private static List<Tuple> fixedFeesTupleListFor(@NonNull final Token token) {
        //
        assert token.customFees() != null;
        return token.customFees().stream()
                .filter(CustomFee::hasFixedFee)
                .filter(fee -> fee.fixedFee() != null)
                .map(fee -> Tuple.of(
                        fee.fixedFee().amount(),
                        headlongAddressOf(fee.fixedFee().denominatingTokenIdOrElse(ZERO_TOKEN_ID)),
                        !fee.fixedFee().hasDenominatingTokenId(),
                        false,
                        headlongAddressOf(fee.feeCollectorAccountIdOrElse(ZERO_ACCOUNT_ID))))
                .toList();
    }

    /**
     * Returns a list of Tuples defined by the {@code FractionalFee} struct
     * {@see https://github.com/hashgraph/hedera-smart-contracts/blob/main/contracts/hts-precompile/IHederaTokenService.sol#L256 }
     * @param token the token to get the fractional fees for
     * @return Tuple encoding of the FractionalFee
     */
    @SuppressWarnings("DataFlowIssue")
    @NonNull
    private static List<Tuple> fractionalFeesTupleListFor(@NonNull final Token token) {
        // array of struct FractionalFee { uint32 numerator; uint32 denominator; uint32 minimumAmount; uint32
        // maximumAmount; bool netOfTransfers; address feeCollector; }
        return token.customFees().stream()
                .filter(CustomFee::hasFractionalFee)
                .filter(fee -> fee.fractionalFee() != null)
                .map(fee -> Tuple.of(
                        fee.fractionalFee()
                                .fractionalAmountOrElse(ZERO_FRACTION)
                                .numerator(),
                        fee.fractionalFee()
                                .fractionalAmountOrElse(ZERO_FRACTION)
                                .denominator(),
                        fee.fractionalFee().minimumAmount(),
                        fee.fractionalFee().maximumAmount(),
                        fee.fractionalFee().netOfTransfers(),
                        headlongAddressOf(fee.feeCollectorAccountIdOrElse(ZERO_ACCOUNT_ID))))
                .toList();
    }

    /**
     * Returns a list of Tuples defined by the {@code RoyaltyFee} struct
     * {@see https://github.com/hashgraph/hedera-smart-contracts/blob/main/contracts/hts-precompile/IHederaTokenService.sol#L279 }
     * @param token the token to get the royalty fees for
     * @return Tuple encoding of the RoyaltyFee
     */
    @SuppressWarnings("DataFlowIssue")
    @NonNull
    private static List<Tuple> royaltyFeesTupleListFor(@NonNull final Token token) {
        // array of struct RoyaltyFee { uint32 numerator; uint32 denominator; uint32 amount; address tokenId; bool
        // useHbarsForPayment; address feeCollector; }
        return token.customFees().stream()
                .filter(CustomFee::hasRoyaltyFee)
                .filter(fee -> fee.royaltyFee() != null)
                .map(fee -> {
                    final var hasFallbackDenominatingTokenId =
                            fee.royaltyFee().fallbackFeeOrElse(ZERO_FIXED_FEE).hasDenominatingTokenId();
                    final var denominatingTokenId = hasFallbackDenominatingTokenId
                            ? fee.royaltyFee().fallbackFee().denominatingTokenId()
                            : ZERO_TOKEN_ID;
                    return Tuple.of(
                            fee.royaltyFee()
                                    .exchangeValueFractionOrElse(ZERO_FRACTION)
                                    .numerator(),
                            fee.royaltyFee()
                                    .exchangeValueFractionOrElse(ZERO_FRACTION)
                                    .denominator(),
                            fee.royaltyFee().fallbackFeeOrElse(ZERO_FIXED_FEE).amount(),
                            headlongAddressOf(denominatingTokenId),
                            !hasFallbackDenominatingTokenId,
                            headlongAddressOf(fee.feeCollectorAccountIdOrElse(ZERO_ACCOUNT_ID)));
                })
                .toList();
    }
}
