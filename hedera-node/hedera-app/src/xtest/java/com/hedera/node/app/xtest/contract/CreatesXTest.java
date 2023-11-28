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

package com.hedera.node.app.xtest.contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;

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
import org.apache.tuweni.bytes.Bytes;

public class CreatesXTest extends AbstractContractXTest {

    private static final Tuple DEFAULT_HEDERA_TOKEN = CreatesXTestConstants.hederaTokenFactory(
            CreatesXTestConstants.NAME,
            CreatesXTestConstants.SYMBOL,
            XTestConstants.OWNER_HEADLONG_ADDRESS,
            CreatesXTestConstants.MEMO,
            true,
            CreatesXTestConstants.MAX_SUPPLY,
            false,
            new Tuple[] {CreatesXTestConstants.TOKEN_KEY, CreatesXTestConstants.TOKEN_KEY_TWO},
            CreatesXTestConstants.EXPIRY);

    private static final Tuple INVALID_ACCOUNT_ID_HEDERA_TOKEN = CreatesXTestConstants.hederaTokenFactory(
            CreatesXTestConstants.NAME,
            CreatesXTestConstants.SYMBOL,
            XTestConstants.INVALID_ACCOUNT_HEADLONG_ADDRESS,
            CreatesXTestConstants.MEMO,
            true,
            CreatesXTestConstants.MAX_SUPPLY,
            false,
            new Tuple[] {CreatesXTestConstants.TOKEN_KEY, CreatesXTestConstants.TOKEN_KEY_TWO},
            CreatesXTestConstants.INVALID_EXPIRY);

    @Override
    protected void doScenarioOperations() {
        // should successfully create fungible token v1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_BIG_INT)
                        .array()),
                XTestConstants.assertSuccess("createFungibleTokenV1"));

        // should successfully create fungible token v2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_LONG)
                        .array()),
                XTestConstants.assertSuccess("createFungibleTokenV2"));

        // should successfully create fungible token v3
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY,
                                CreatesXTestConstants.DECIMALS)
                        .array()),
                XTestConstants.assertSuccess("createFungibleTokenV3"));

        // should successfully create fungible token without TokenKeys (empty array)
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(
                                CreatesXTestConstants.hederaTokenFactory(
                                        CreatesXTestConstants.NAME,
                                        CreatesXTestConstants.SYMBOL,
                                        XTestConstants.OWNER_HEADLONG_ADDRESS,
                                        CreatesXTestConstants.MEMO,
                                        true,
                                        CreatesXTestConstants.MAX_SUPPLY,
                                        false,
                                        new Tuple[] {},
                                        CreatesXTestConstants.EXPIRY),
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_BIG_INT)
                        .array()),
                XTestConstants.assertSuccess("createFungibleTokenV1 - sans keys"));

        // should revert on missing expiry

        // should revert on invalid account address
        runHtsCallAndExpectRevert(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(
                                CreatesXTestConstants.hederaTokenFactory(
                                        CreatesXTestConstants.NAME,
                                        CreatesXTestConstants.SYMBOL,
                                        XTestConstants.OWNER_HEADLONG_ADDRESS,
                                        CreatesXTestConstants.MEMO,
                                        true,
                                        CreatesXTestConstants.MAX_SUPPLY,
                                        false,
                                        new Tuple[] {CreatesXTestConstants.TOKEN_INVALID_KEY},
                                        CreatesXTestConstants.EXPIRY),
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_BIG_INT)
                        .array()),
                INVALID_ADMIN_KEY,
                "createFungibleTokenV1 - invalid admin key");

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(
                                CreatesXTestConstants.hederaTokenFactory(
                                        CreatesXTestConstants.NAME,
                                        CreatesXTestConstants.SYMBOL,
                                        XTestConstants.OWNER_HEADLONG_ADDRESS,
                                        CreatesXTestConstants.MEMO,
                                        true,
                                        CreatesXTestConstants.MAX_SUPPLY,
                                        false,
                                        new Tuple[] {CreatesXTestConstants.TOKEN_KEY},
                                        Tuple.of(
                                                CreatesXTestConstants.SECOND,
                                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                                1L)),
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_BIG_INT)
                        .array()),
                INVALID_RENEWAL_PERIOD,
                "createFungibleTokenV1 - invalid renewal period");

        // should successfully create fungible token with custom fees v1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_BIG_INT,
                                // FixedFee
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                // FractionalFee
                                new Tuple[] {CreatesXTestConstants.FRACTIONAL_FEE})
                        .array()),
                XTestConstants.assertSuccess("createFungibleWithCustomFeesV1"));

        // should successfully create fungible token with custom fees v2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_LONG,
                                // FixedFee
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                // FractionalFee
                                new Tuple[] {CreatesXTestConstants.FRACTIONAL_FEE})
                        .array()),
                XTestConstants.assertSuccess("createFungibleWithCustomFeesV2"));

        // should successfully create fungible token with custom fees v3
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY,
                                CreatesXTestConstants.DECIMALS,
                                // FixedFee
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                // FractionalFee
                                new Tuple[] {CreatesXTestConstants.FRACTIONAL_FEE})
                        .array()),
                XTestConstants.assertSuccess("createFungibleWithCustomFeesV3"));

        // should successfully create non-fungible token without custom fees v1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                XTestConstants.assertSuccess("createNonFungibleTokenV1"));

        // should successfully create non-fungible token without custom fees v2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                XTestConstants.assertSuccess("createNonFungibleTokenV2"));

        // should successfully create non-fungible token without custom fees v3
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                XTestConstants.assertSuccess("createNonFungibleTokenV3"));

        // should successfully create non-fungible token with custom fees v1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                new Tuple[] {CreatesXTestConstants.ROYALTY_FEE})
                        .array()),
                XTestConstants.assertSuccess("createNonFungibleWithCustomFeesV1"));

        // should successfully create non-fungible token with custom fees v2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                new Tuple[] {CreatesXTestConstants.ROYALTY_FEE})
                        .array()),
                XTestConstants.assertSuccess("createNonFungibleWithCustomFeesV2"));

        // should successfully create non-fungible token with custom fees v3
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                new Tuple[] {CreatesXTestConstants.ROYALTY_FEE})
                        .array()),
                XTestConstants.assertSuccess("createNonFungibleWithCustomFeesV3"));

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(
                                INVALID_ACCOUNT_ID_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_BIG_INT)
                        .array()),
                INVALID_ACCOUNT_ID,
                "createFungibleTokenV1 - invalid treasury account");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                INVALID_ACCOUNT_ID_HEDERA_TOKEN,
                                CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT,
                                CreatesXTestConstants.DECIMALS_BIG_INT,
                                // FixedFee
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                // FractionalFee
                                new Tuple[] {CreatesXTestConstants.FRACTIONAL_FEE})
                        .array()),
                INVALID_ACCOUNT_ID,
                "createFungibleWithCustomFeesV1 - invalid treasury account");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        // Changed to `INVALID_ACCOUNT_ID` see {@link
        // com/hedera/node/app/service/token/impl/handlers/TokenCreateHandler#95 }
        runHtsCallAndExpectRevert(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(INVALID_ACCOUNT_ID_HEDERA_TOKEN)
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleTokenV1 - invalid treasury account");

        // should revert with `INVALID_TREASURY_ACCOUNT_FOR_TOKEN` when passing invalid address for the treasury account
        runHtsCallAndExpectRevert(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                INVALID_ACCOUNT_ID_HEDERA_TOKEN,
                                new Tuple[] {CreatesXTestConstants.FIXED_FEE},
                                new Tuple[] {CreatesXTestConstants.ROYALTY_FEE})
                        .array()),
                INVALID_ACCOUNT_ID,
                "createNonFungibleWithCustomFeesV1 - invalid treasury account");
    }

    @Override
    protected long initialEntityNum() {
        return CreatesXTestConstants.NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = withSenderAlias(new HashMap<>());
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.SENDER_ADDRESS).build(), XTestConstants.SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc20Relation(tokenRelationships, XTestConstants.OWNER_ID, 800L);
        return tokenRelationships;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final Map<AccountID, Account> accounts = new HashMap<>();
        accounts.put(
                XTestConstants.SENDER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.SENDER_ID)
                        .alias(XTestConstants.SENDER_ADDRESS)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .tinybarBalance(100 * XTestConstants.ONE_HBAR)
                        .build());
        accounts.put(
                XTestConstants.OWNER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.OWNER_ID)
                        .alias(XTestConstants.OWNER_ADDRESS)
                        .key(XTestConstants.SENDER_CONTRACT_ID_KEY)
                        .build());
        return accounts;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                XTestConstants.ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .treasuryAccountId(XTestConstants.OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(XTestConstants.AN_ED25519_KEY)
                        .totalSupply(800L)
                        .build());
        return tokens;
    }
}
