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

import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.assertSuccess;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class CreateXTest extends AbstractContractXTest {

    private static final long INITIAL_TOTAL_SUPPLY = 10L;
    private static final int DECIMALS = 8;

    @Override
    protected void doScenarioOperations() {
        // should successfully create fungible token
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN
                        .encodeCallWithArgs(
                                Tuple.of(
                                        "Name",
                                        "Symbol",
                                        OWNER_HEADLONG_ADDRESS,
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
                                        Tuple.of(123L, OWNER_HEADLONG_ADDRESS, 2592000L)),
                                INITIAL_TOTAL_SUPPLY,
                                DECIMALS)
                        .array()),
                assertSuccess());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        return aliases;
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
                A_TOKEN_ID,
                Token.newBuilder()
                        .name("Name")
                        .symbol("Symbol")
                        .treasuryAccountId(OWNER_ID)
                        .memo("Memo")
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .maxSupply(10L)
                        .totalSupply(10L)
                        .build());
        return tokens;
    }
}
