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

package contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.TokenTupleUtils.typedKeyTupleFor;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.customfees.TokenCustomFeesTranslator.TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.fungibletokeninfo.FungibleTokenInfoTranslator.FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.nfttokeninfo.NftTokenInfoTranslator.NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenexpiry.TokenExpiryTranslator.TOKEN_EXPIRY;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokeninfo.TokenInfoTranslator.TOKEN_INFO;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey.TokenKeyTranslator.TOKEN_KEY;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static contract.MiscViewsXTestConstants.ERC_USER_ID;
import static contract.MiscViewsXTestConstants.OPERATOR_ID;
import static contract.XTestConstants.ERC20_TOKEN_ID;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.TokenTupleUtils.TokenKeyType;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

public class ClassicViewsXTestConstants {
    public static final String LEDGER_ID = "03";
    private static final int SUCCESS_INT = 22;
    static final FileID CLASSIC_VIEWS_INITCODE_FILE_ID = new FileID(0, 0, 1029L);
    static final ContractID CLASSIC_QUERIES_X_TEST_ID =
            ContractID.newBuilder().contractNum(1030L).build();
    static final String SUCCESS_RESPONSE_CODE = "0000000000000000000000000000000000000000000000000000000000000016";
    static final String SUCCESS_RESPONSE_CODE_RETURNING_TRUE =
            SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000001";
    static final String SUCCESS_RESPONSE_CODE_RETURNING_FALSE =
            SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000";
    // IS_FROZEN flag for token is false
    static final Bytes TOKEN_IS_FROZEN = Bytes.fromHex(SUCCESS_RESPONSE_CODE_RETURNING_FALSE);
    // IS_KYC flag for token is true - set in ClassicViewsXTest.initialTokenRelationships()
    static final Bytes TOKEN_IS_KYC = Bytes.fromHex(SUCCESS_RESPONSE_CODE_RETURNING_TRUE);
    // the token is truly a token
    static final Bytes TOKEN_IS_TOKEN = Bytes.fromHex(SUCCESS_RESPONSE_CODE_RETURNING_TRUE);
    // the token is fungible
    static final Bytes TOKEN_TYPE_FUNGIBLE = Bytes.fromHex(SUCCESS_RESPONSE_CODE_RETURNING_FALSE);
    // default freeze status is true
    static final Bytes TOKEN_FROZEN_STATUS = Bytes.fromHex(SUCCESS_RESPONSE_CODE_RETURNING_TRUE);
    // default kyc status is true
    static final Bytes TOKEN_KYC_GRANTED_STATUS = Bytes.fromHex(SUCCESS_RESPONSE_CODE_RETURNING_TRUE);
    static final long EXPIRATION_SECONDS = 100L;
    static final long AUTORENEW_SECONDS = 200L;
    static final ByteBuffer EXPECTED_TOKEN_EXPIRY = TOKEN_EXPIRY
            .getOutputs()
            .encodeElements(
                    SUCCESS_INT, Tuple.of(EXPIRATION_SECONDS, headlongAddressOf(OPERATOR_ID), AUTORENEW_SECONDS));
    static final ContractID A_CONTRACT_ID =
            ContractID.newBuilder().contractNum(666L).build();
    static final ContractID B_CONTRACT_ID =
            ContractID.newBuilder().contractNum(777L).build();
    static final Key ADMIN_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();
    static final Key KYC_KEY = Key.newBuilder()
            .ecdsaSecp256k1(Bytes.fromHex("0202020202020202020202020202020202020202020202020202020202020202"))
            .build();
    static final Key FREEZE_KEY = Key.newBuilder().contractID(A_CONTRACT_ID).build();
    static final Key WIPE_KEY =
            Key.newBuilder().delegatableContractId(B_CONTRACT_ID).build();
    static final Key SUPPLY_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0303030303030303030303030303030303030303030303030303030303030303"))
            .build();
    static final Key FEE_SCHEDULE_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0404040404040404040404040404040404040404040404040404040404040404"))
            .build();
    static final Key PAUSE_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0505050505050505050505050505050505050505050505050505050505050505"))
            .build();

    static final ByteBuffer returnExpectedKey(@NonNull final Key key) {
        return TOKEN_KEY
                .getOutputs()
                .encodeElements(
                        SUCCESS_INT,
                        Tuple.of(
                                false,
                                headlongAddressOf(key.contractIDOrElse(ZERO_CONTRACT_ID)),
                                key.ed25519OrElse(Bytes.EMPTY).toByteArray(),
                                key.ecdsaSecp256k1OrElse(Bytes.EMPTY).toByteArray(),
                                headlongAddressOf(key.delegatableContractIdOrElse(ZERO_CONTRACT_ID))));
    }

    static final List<Tuple> KEYLIST = List.of(
            typedKeyTupleFor(BigInteger.valueOf(TokenKeyType.ADMIN_KEY.value()), ADMIN_KEY),
            typedKeyTupleFor(BigInteger.valueOf(TokenKeyType.KYC_KEY.value()), KYC_KEY),
            typedKeyTupleFor(BigInteger.valueOf(TokenKeyType.FREEZE_KEY.value()), FREEZE_KEY),
            typedKeyTupleFor(BigInteger.valueOf(TokenKeyType.WIPE_KEY.value()), WIPE_KEY),
            typedKeyTupleFor(BigInteger.valueOf(TokenKeyType.SUPPLY_KEY.value()), SUPPLY_KEY),
            typedKeyTupleFor(BigInteger.valueOf(TokenKeyType.FEE_SCHEDULE_KEY.value()), FEE_SCHEDULE_KEY),
            typedKeyTupleFor(BigInteger.valueOf(TokenKeyType.PAUSE_KEY.value()), PAUSE_KEY));

    static final CustomFee FIXED_TOKEN_FEES = CustomFee.newBuilder()
            .fixedFee(FixedFee.newBuilder()
                    .amount(3)
                    .denominatingTokenId(ERC20_TOKEN_ID)
                    .build())
            .feeCollectorAccountId(OPERATOR_ID)
            .build();
    static final CustomFee FRACTION_FEES = CustomFee.newBuilder()
            .fractionalFee(FractionalFee.newBuilder()
                    .fractionalAmount(
                            Fraction.newBuilder().numerator(1).denominator(100).build())
                    .minimumAmount(2)
                    .maximumAmount(4)
                    .netOfTransfers(true)
                    .build())
            .feeCollectorAccountId(OPERATOR_ID)
            .build();
    static final CustomFee ROYALTY_FEE_WITH_FALLBACK = CustomFee.newBuilder()
            .royaltyFee(RoyaltyFee.newBuilder()
                    .exchangeValueFraction(
                            Fraction.newBuilder().numerator(2).denominator(50).build())
                    .fallbackFee(FixedFee.newBuilder()
                            .amount(5)
                            .denominatingTokenId(ERC20_TOKEN_ID)
                            .build())
                    .build())
            .feeCollectorAccountId(OPERATOR_ID)
            .build();
    static final List<CustomFee> CUSTOM_FEES = List.of(FIXED_TOKEN_FEES, FRACTION_FEES, ROYALTY_FEE_WITH_FALLBACK);
    public static final List<Tuple> EXPECTED_FIXED_CUSTOM_FEES =
            List.of(Tuple.of(3L, headlongAddressOf(ERC20_TOKEN_ID), false, false, headlongAddressOf(OPERATOR_ID)));
    public static final List<Tuple> EXPECTED_FRACTIONAL_CUSTOM_FEES =
            List.of(Tuple.of(1L, 100L, 2L, 4L, true, headlongAddressOf(OPERATOR_ID)));
    public static final List<Tuple> EXPECTED_ROYALTY_CUSTOM_FEES =
            List.of(Tuple.of(2L, 50L, 5L, headlongAddressOf(ERC20_TOKEN_ID), false, headlongAddressOf(OPERATOR_ID)));
    static final ByteBuffer EXPECTED_CUSTOM_FEES = TOKEN_CUSTOM_FEES
            .getOutputs()
            .encodeElements(
                    SUCCESS_INT,
                    EXPECTED_FIXED_CUSTOM_FEES.toArray(new Tuple[EXPECTED_FIXED_CUSTOM_FEES.size()]),
                    EXPECTED_FRACTIONAL_CUSTOM_FEES.toArray(new Tuple[EXPECTED_FRACTIONAL_CUSTOM_FEES.size()]),
                    EXPECTED_ROYALTY_CUSTOM_FEES.toArray(new Tuple[EXPECTED_ROYALTY_CUSTOM_FEES.size()]));

    static final ByteBuffer EXPECTED_TOKEN_INFO = TOKEN_INFO
            .getOutputs()
            .encodeElements(
                    SUCCESS.protoOrdinal(),
                    Tuple.of(
                            Tuple.of(
                                    "20 Coin",
                                    "SYM20",
                                    headlongAddressOf(ZERO_ACCOUNT_ID),
                                    "20 Coin Memo",
                                    true,
                                    999L,
                                    true,
                                    KEYLIST.toArray(new Tuple[0]),
                                    Tuple.of(EXPIRATION_SECONDS, headlongAddressOf(OPERATOR_ID), AUTORENEW_SECONDS)),
                            888L,
                            false,
                            true,
                            true,
                            EXPECTED_FIXED_CUSTOM_FEES.toArray(new Tuple[0]),
                            EXPECTED_FRACTIONAL_CUSTOM_FEES.toArray(new Tuple[0]),
                            EXPECTED_ROYALTY_CUSTOM_FEES.toArray(new Tuple[0]),
                            LEDGER_ID));
    static final ByteBuffer EXPECTED_FUNGIBLE_TOKEN_INFO = FUNGIBLE_TOKEN_INFO
            .getOutputs()
            .encodeElements(
                    SUCCESS.protoOrdinal(),
                    Tuple.of(
                            Tuple.of(
                                    Tuple.of(
                                            "20 Coin",
                                            "SYM20",
                                            headlongAddressOf(ZERO_ACCOUNT_ID),
                                            "20 Coin Memo",
                                            true,
                                            999L,
                                            true,
                                            KEYLIST.toArray(new Tuple[0]),
                                            Tuple.of(
                                                    EXPIRATION_SECONDS,
                                                    headlongAddressOf(OPERATOR_ID),
                                                    AUTORENEW_SECONDS)),
                                    888L,
                                    false,
                                    true,
                                    true,
                                    EXPECTED_FIXED_CUSTOM_FEES.toArray(new Tuple[0]),
                                    EXPECTED_FRACTIONAL_CUSTOM_FEES.toArray(new Tuple[0]),
                                    EXPECTED_ROYALTY_CUSTOM_FEES.toArray(new Tuple[0]),
                                    LEDGER_ID),
                            2));
    static final ByteBuffer EXPECTED_NON_FUNGIBLE_TOKEN_INFO = NON_FUNGIBLE_TOKEN_INFO
            .getOutputs()
            .encodeElements(
                    SUCCESS.protoOrdinal(),
                    Tuple.of(
                            Tuple.of(
                                    Tuple.of(
                                            "721 Unique Things",
                                            "SYM721",
                                            headlongAddressOf(ZERO_ACCOUNT_ID),
                                            "721 Unique Things Memo",
                                            true,
                                            999L,
                                            true,
                                            KEYLIST.toArray(new Tuple[0]),
                                            Tuple.of(
                                                    EXPIRATION_SECONDS,
                                                    headlongAddressOf(OPERATOR_ID),
                                                    AUTORENEW_SECONDS)),
                                    888L,
                                    false,
                                    true,
                                    true,
                                    EXPECTED_FIXED_CUSTOM_FEES.toArray(new Tuple[0]),
                                    EXPECTED_FRACTIONAL_CUSTOM_FEES.toArray(new Tuple[0]),
                                    EXPECTED_ROYALTY_CUSTOM_FEES.toArray(new Tuple[0]),
                                    LEDGER_ID),
                            1L,
                            headlongAddressOf(ERC_USER_ID),
                            0L,
                            com.hedera.pbj.runtime.io.buffer.Bytes.wrap("https://example.com/721/1")
                                    .toByteArray(),
                            headlongAddressOf(OPERATOR_ID)));

    static final Function GET_IS_FROZEN = new Function("isFrozenPublic(address,address)");
    static final Function GET_IS_KYC = new Function("isKycPublic(address,address)");
    static final Function GET_IS_TOKEN = new Function("isTokenPublic(address)");
    static final Function GET_TOKEN_TYPE = new Function("getTokenTypePublic(address)");
    static final Function GET_DEFAULT_FREEZE_STATUS = new Function("getTokenDefaultFreezeStatusPublic(address)");
    static final Function GET_DEFAULT_KYC_STATUS = new Function("getTokenDefaultKycStatusPublic(address)");
    static final Function GET_TOKEN_EXPIRY = new Function("getTokenExpiryInfoPublic(address)");
    static final Function GET_TOKEN_KEY = new Function("getTokenKeyPublic(address,uint)");
    static final Function GET_TOKEN_CUSTOM_FEES = new Function("getTokenCustomFeesPublic(address)");
    static final Function GET_TOKEN_INFO = new Function("getTokenInfoPublic(address)");
    static final Function GET_FUNGIBLE_TOKEN_INFO = new Function("getFungibleTokenInfoPublic(address)");
    static final Function GET_NON_FUNGIBLE_TOKEN_INFO = new Function("getNonFungibleTokenInfoPublic(address,int64)");
}
