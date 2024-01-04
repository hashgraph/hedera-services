/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static contract.CreatesXTestConstants.EXPIRY;
import static contract.CreatesXTestConstants.FIXED_FEE;
import static contract.CreatesXTestConstants.INVALID_EXPIRY;
import static contract.CreatesXTestConstants.MAX_SUPPLY;
import static contract.CreatesXTestConstants.MEMO;
import static contract.CreatesXTestConstants.NAME;
import static contract.CreatesXTestConstants.NEXT_ENTITY_NUM;
import static contract.CreatesXTestConstants.ROYALTY_FEE;
import static contract.CreatesXTestConstants.SECOND;
import static contract.CreatesXTestConstants.SYMBOL;
import static contract.CreatesXTestConstants.TOKEN_ADMIN_KEY;
import static contract.CreatesXTestConstants.TOKEN_INVALID_SUPPLY_KEY;
import static contract.CreatesXTestConstants.TOKEN_KEY_TWO;
import static contract.CreatesXTestConstants.TOKEN_SUPPLY_KEY;
import static contract.CreatesXTestConstants.hederaTokenFactory;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.COINBASE_ID;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.INVALID_ACCOUNT_HEADLONG_ADDRESS;
import static contract.XTestConstants.ONE_HBAR;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.addErc20Relation;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises create a token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V1}.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V1}. This should fail with code TOKEN_HAS_NO_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V1}. This should fail with code INVALID_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V1}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V1}. This should fail with code INVALID_ACCOUNT_ID.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V2}.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V2}. This should fail with code TOKEN_HAS_NO_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V2}. This should fail with code INVALID_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V2}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V2}. This should fail with code INVALID_ACCOUNT_ID.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V3}.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V3}. This should fail with code TOKEN_HAS_NO_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V3}. This should fail with code INVALID_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V3}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V3}. This should fail with code INVALID_ACCOUNT_ID.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1}.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1}. This should fail with code TOKEN_HAS_NO_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1}. This should fail with code INVALID_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1}. This should fail with code INVALID_ACCOUNT_ID.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2}.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2}. This should fail with code TOKEN_HAS_NO_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2}. This should fail with code INVALID_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2}. This should fail with code INVALID_ACCOUNT_ID.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3}.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3}. This should fail with code TOKEN_HAS_NO_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3}. This should fail with code INVALID_SUPPLY_KEY.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Create token {@code ERC721_TOKEN} via {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3}. This should fail with code INVALID_ACCOUNT_ID.</li>
 * </ol>
 */
public class CreatesERC721XTest extends AbstractContractXTest {

    private static final Tuple DEFAULT_HEDERA_TOKEN = hederaTokenFactory(
            NAME,
            SYMBOL,
            OWNER_HEADLONG_ADDRESS,
            MEMO,
            true,
            MAX_SUPPLY,
            false,
            new Tuple[] {TOKEN_ADMIN_KEY, TOKEN_KEY_TWO},
            EXPIRY);

    private static final Tuple INVALID_ACCOUNT_ID_HEDERA_TOKEN = hederaTokenFactory(
            NAME,
            SYMBOL,
            INVALID_ACCOUNT_HEADLONG_ADDRESS,
            MEMO,
            true,
            MAX_SUPPLY,
            false,
            new Tuple[] {TOKEN_ADMIN_KEY, TOKEN_KEY_TWO},
            INVALID_EXPIRY);

    @Override
    protected void doScenarioOperations() {
        final AtomicInteger tokenId = new AtomicInteger(1004);
        // should successfully create non-fungible token without custom fees v1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(),
                                        asLongZeroHeadlongAddress(new TokenID(0, 0, tokenId.getAndIncrement())))
                                .array()),
                        output));

        // should revert without provided supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {},
                                EXPIRY))
                        .array()),
                TOKEN_HAS_NO_SUPPLY_KEY,
                "createNonFungibleTokenV1 - no supply key");

        // should revert on invalid supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {TOKEN_INVALID_SUPPLY_KEY},
                                EXPIRY))
                        .array()),
                INVALID_SUPPLY_KEY,
                "createNonFungibleTokenV1 - invalid supply key");

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {TOKEN_SUPPLY_KEY},
                                Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, 1L)))
                        .array()),
                INVALID_RENEWAL_PERIOD,
                "createNonFungibleTokenV1 - invalid renewal period");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(INVALID_ACCOUNT_ID_HEDERA_TOKEN)
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleTokenV1 - invalid treasury account");

        // should successfully create non-fungible token without custom fees v2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(),
                                        asLongZeroHeadlongAddress(new TokenID(0, 0, tokenId.getAndIncrement())))
                                .array()),
                        output));

        // should revert without provided supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {},
                                EXPIRY))
                        .array()),
                TOKEN_HAS_NO_SUPPLY_KEY,
                "createNonFungibleTokenV2 - no supply key");

        // should revert on invalid supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {TOKEN_INVALID_SUPPLY_KEY},
                                EXPIRY))
                        .array()),
                INVALID_SUPPLY_KEY,
                "createNonFungibleTokenV2 - invalid supply key");

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {TOKEN_SUPPLY_KEY},
                                Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, 1L)))
                        .array()),
                INVALID_RENEWAL_PERIOD,
                "createNonFungibleTokenV2 - invalid renewal period");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(INVALID_ACCOUNT_ID_HEDERA_TOKEN)
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleTokenV2 - invalid treasury account");

        // should successfully create non-fungible token without custom fees v3
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(),
                                        asLongZeroHeadlongAddress(new TokenID(0, 0, tokenId.getAndIncrement())))
                                .array()),
                        output));

        // should revert without provided supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {},
                                EXPIRY))
                        .array()),
                TOKEN_HAS_NO_SUPPLY_KEY,
                "createNonFungibleTokenV3 - no supply key");

        // should revert on invalid supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {TOKEN_INVALID_SUPPLY_KEY},
                                EXPIRY))
                        .array()),
                INVALID_SUPPLY_KEY,
                "createNonFungibleTokenV3 - invalid supply key");

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(hederaTokenFactory(
                                NAME,
                                SYMBOL,
                                OWNER_HEADLONG_ADDRESS,
                                MEMO,
                                true,
                                MAX_SUPPLY,
                                false,
                                new Tuple[] {TOKEN_SUPPLY_KEY},
                                Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, 1L)))
                        .array()),
                INVALID_RENEWAL_PERIOD,
                "createNonFungibleTokenV3 - invalid renewal period");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(INVALID_ACCOUNT_ID_HEDERA_TOKEN)
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleTokenV3 - invalid treasury account");

        // should successfully create non-fungible token with custom fees v1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(),
                                        asLongZeroHeadlongAddress(new TokenID(0, 0, tokenId.getAndIncrement())))
                                .array()),
                        output));

        // should revert without provided supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {},
                                        EXPIRY),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                TOKEN_HAS_NO_SUPPLY_KEY,
                "createNonFungibleWithCustomFeesV1 - no supply key");

        // should revert on invalid supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_INVALID_SUPPLY_KEY},
                                        EXPIRY),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_SUPPLY_KEY,
                "createNonFungibleWithCustomFeesV1 - invalid supply key");

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_SUPPLY_KEY},
                                        Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, 1L)),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_RENEWAL_PERIOD,
                "createNonFungibleWithCustomFeesV1 - invalid renewal period");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                INVALID_ACCOUNT_ID_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleWithCustomFeesV1 - invalid treasury account");

        // should successfully create non-fungible token with custom fees v2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(),
                                        asLongZeroHeadlongAddress(new TokenID(0, 0, tokenId.getAndIncrement())))
                                .array()),
                        output));

        // should revert without provided supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {},
                                        EXPIRY),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                TOKEN_HAS_NO_SUPPLY_KEY,
                "createNonFungibleWithCustomFeesV2 - no supply key");

        // should revert on invalid supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_INVALID_SUPPLY_KEY},
                                        EXPIRY),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_SUPPLY_KEY,
                "createNonFungibleWithCustomFeesV2 - invalid supply key");

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_SUPPLY_KEY},
                                        Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, 1L)),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_RENEWAL_PERIOD,
                "createNonFungibleWithCustomFeesV2 - invalid renewal period");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(
                                INVALID_ACCOUNT_ID_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleWithCustomFeesV2 - invalid treasury account");

        // should successfully create non-fungible token with custom fees v3
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(),
                                        asLongZeroHeadlongAddress(new TokenID(0, 0, tokenId.getAndIncrement())))
                                .array()),
                        output));

        // should revert without provided supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {},
                                        EXPIRY),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                TOKEN_HAS_NO_SUPPLY_KEY,
                "createNonFungibleWithCustomFeesV3 - no supply key");

        // should revert on invalid supply key
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_INVALID_SUPPLY_KEY},
                                        EXPIRY),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_SUPPLY_KEY,
                "createNonFungibleWithCustomFeesV3 - invalid supply key");

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_SUPPLY_KEY},
                                        Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, 1L)),
                                new Tuple[] {FIXED_FEE},
                                new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_RENEWAL_PERIOD,
                "createNonFungibleWithCustomFeesV3 - invalid renewal period");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(
                                INVALID_ACCOUNT_ID_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleWithCustomFeesV3 - invalid treasury account");
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = withSenderAlias(new HashMap<>());
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, OWNER_ID, 800L);
        return tokenRelationships;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final Map<AccountID, Account> accounts = new HashMap<>();
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(SENDER_ID)
                        .alias(SENDER_ADDRESS)
                        .key(AN_ED25519_KEY)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(OWNER_ADDRESS)
                        .key(SENDER_CONTRACT_ID_KEY)
                        .build());
        return accounts;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(800L)
                        .build());
        return tokens;
    }
}
