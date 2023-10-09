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

import static contract.CreatesXTestConstants.DECIMALS;
import static contract.CreatesXTestConstants.DECIMALS_BIG_INT;
import static contract.CreatesXTestConstants.DECIMALS_LONG;
import static contract.CreatesXTestConstants.EXPIRY;
import static contract.CreatesXTestConstants.FIXED_FEE;
import static contract.CreatesXTestConstants.FRACTIONAL_FEE;
import static contract.CreatesXTestConstants.INITIAL_TOTAL_SUPPLY;
import static contract.CreatesXTestConstants.INITIAL_TOTAL_SUPPLY_BIG_INT;
import static contract.CreatesXTestConstants.MAX_SUPPLY;
import static contract.CreatesXTestConstants.MEMO;
import static contract.CreatesXTestConstants.NAME;
import static contract.CreatesXTestConstants.NEXT_ENTITY_NUM;
import static contract.CreatesXTestConstants.ROYALTY_FEE;
import static contract.CreatesXTestConstants.SECOND;
import static contract.CreatesXTestConstants.SYMBOL;
import static contract.CreatesXTestConstants.TOKEN_INVALID_KEY;
import static contract.CreatesXTestConstants.TOKEN_KEY;
import static contract.CreatesXTestConstants.TOKEN_KEY_TWO;
import static contract.CreatesXTestConstants.hederaTokenFactory;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.assertSuccess;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
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

    private static final Tuple DEFAULT_HEDERA_TOKEN = hederaTokenFactory(
            NAME,
            SYMBOL,
            OWNER_HEADLONG_ADDRESS,
            MEMO,
            true,
            MAX_SUPPLY,
            false,
            new Tuple[] {TOKEN_KEY, TOKEN_KEY_TWO},
            EXPIRY);

    @Override
    protected void doScenarioOperations() {
        // should successfully create fungible token v1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, INITIAL_TOTAL_SUPPLY_BIG_INT, DECIMALS_BIG_INT)
                        .array()),
                assertSuccess());

        // should successfully create fungible token v2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, INITIAL_TOTAL_SUPPLY_BIG_INT, DECIMALS_LONG)
                        .array()),
                assertSuccess());

        // should successfully create fungible token v3
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, INITIAL_TOTAL_SUPPLY, DECIMALS)
                        .array()),
                assertSuccess());

        // should successfully create fungible token without TokenKeys (empty array)
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
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
                                INITIAL_TOTAL_SUPPLY_BIG_INT,
                                DECIMALS_BIG_INT)
                        .array()),
                assertSuccess());

        // should revert on missing expiry

        // should revert on invalid account address
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_INVALID_KEY},
                                        EXPIRY),
                                INITIAL_TOTAL_SUPPLY_BIG_INT,
                                DECIMALS_BIG_INT)
                        .array()),
                ResponseCodeEnum.INVALID_ADMIN_KEY);

        // should revert with autoRenewPeriod less than 2592000
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(
                                hederaTokenFactory(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        new Tuple[] {TOKEN_KEY},
                                        Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, 1L)),
                                INITIAL_TOTAL_SUPPLY_BIG_INT,
                                DECIMALS_BIG_INT)
                        .array()),
                ResponseCodeEnum.INVALID_RENEWAL_PERIOD);

        // should successfully create fungible token with custom fees v1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                INITIAL_TOTAL_SUPPLY_BIG_INT,
                                DECIMALS_BIG_INT,
                                // FixedFee
                                new Tuple[] {FIXED_FEE},
                                // FractionalFee
                                new Tuple[] {FRACTIONAL_FEE})
                        .array()),
                assertSuccess());

        // should successfully create fungible token with custom fees v2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                INITIAL_TOTAL_SUPPLY_BIG_INT,
                                DECIMALS_LONG,
                                // FixedFee
                                new Tuple[] {FIXED_FEE},
                                // FractionalFee
                                new Tuple[] {FRACTIONAL_FEE})
                        .array()),
                assertSuccess());

        // should successfully create fungible token with custom fees v3
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(
                                DEFAULT_HEDERA_TOKEN,
                                INITIAL_TOTAL_SUPPLY,
                                DECIMALS,
                                // FixedFee
                                new Tuple[] {FIXED_FEE},
                                // FractionalFee
                                new Tuple[] {FRACTIONAL_FEE})
                        .array()),
                assertSuccess());

        // should successfully create non-fungible token without custom fees v1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                assertSuccess());

        // should successfully create non-fungible token without custom fees v2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                assertSuccess());

        // should successfully create non-fungible token without custom fees v3
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN)
                        .array()),
                assertSuccess());

        // should revert when token has no supplyKey

        // should successfully create non-fungible token with custom fees v1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                assertSuccess());

        // should successfully create non-fungible token with custom fees v2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                assertSuccess());

        // should successfully create non-fungible token with custom fees v3
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .encodeCallWithArgs(DEFAULT_HEDERA_TOKEN, new Tuple[] {FIXED_FEE}, new Tuple[] {ROYALTY_FEE})
                        .array()),
                assertSuccess());
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
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
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(SENDER_ADDRESS)
                        .smartContract(true)
                        .key(SENDER_CONTRACT_ID_KEY)
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
