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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateTranslator;
import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class UpdatesXTest extends AbstractContractXTest {
    private static final String NEW_NAME = "New name";
    private static final String NEW_SYMBOL = "New symbol";

    private static final long TIMESTAMP = Instant.now().getEpochSecond() + 1000;

    @Override
    protected void doScenarioOperations() {
        // TODO: possible to pass null?
        // Successfully update token via TOKEN_UPDATE_INFO V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(SENDER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(TIMESTAMP, asHeadlongAddress(SENDER_ADDRESS.toByteArray()), 2592000L)))
                        .array()),
                assertSuccess());

        // Successfully update token via TOKEN_UPDATE_INFO V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(SENDER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(TIMESTAMP, asHeadlongAddress(SENDER_ADDRESS.toByteArray()), 2592000L)))
                        .array()),
                assertSuccess());
        //
        //        // Successfully update token via TOKEN_UPDATE_INFO V3
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(SENDER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {
                                            Tuple.of(
                                                    BigInteger.valueOf(1L),
                                                    Tuple.of(
                                                            true,
                                                            RECEIVER_HEADLONG_ADDRESS,
                                                            new byte[] {},
                                                            new byte[] {},
                                                            RECEIVER_HEADLONG_ADDRESS))
                                        },
                                        // Expiry
                                        Tuple.of(TIMESTAMP, asHeadlongAddress(SENDER_ADDRESS.toByteArray()), 2592000L)))
                        .array()),
                assertSuccess());
    }

    @Override
    protected void assertExpectedTokens(@NonNull ReadableKVState<TokenID, Token> tokens) {
        final var erc20Token = tokens.get(ERC20_TOKEN_ID);
        assertNotNull(erc20Token);
        assertEquals(NEW_NAME, erc20Token.name());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .treasuryAccountId(SENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .adminKey(SENDER_CONTRACT_ID_KEY)
                        .autoRenewAccountId(SENDER_ID)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, SENDER_ID, 0);
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
                        .key(SENDER_CONTRACT_ID_KEY)
                        .smartContract(true)
                        .build());
        return accounts;
    }
}
