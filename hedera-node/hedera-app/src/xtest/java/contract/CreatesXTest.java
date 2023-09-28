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

public class CreatesXTest extends AbstractContractXTest {

    private static final long INITIAL_TOTAL_SUPPLY = 10L;
    private static final int DECIMALS = 8;
    private static final String NAME = "name";
    private static final String SYMBOL = "symbol";
    private static final String MEMO = "memo";
    private static final long MAX_SUPPLY = 1000L;
    private static final long KEY_TYPE = 1L;
    private static final long SECOND = 123L;
    private static final long AUTO_RENEW_PERIOD = 2592000L;

    @Override
    protected void doScenarioOperations() {
        // should successfully create fungible token
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN
                        .encodeCallWithArgs(
                                Tuple.of(
                                        NAME,
                                        SYMBOL,
                                        OWNER_HEADLONG_ADDRESS,
                                        MEMO,
                                        true,
                                        MAX_SUPPLY,
                                        false,
                                        // TokenKey
                                        new Tuple[] {
                                            Tuple.of(
                                                    BigInteger.valueOf(KEY_TYPE),
                                                    Tuple.of(
                                                            true,
                                                            RECEIVER_HEADLONG_ADDRESS,
                                                            new byte[] {},
                                                            new byte[] {},
                                                            RECEIVER_HEADLONG_ADDRESS))
                                        },
                                        // Expiry
                                        Tuple.of(SECOND, OWNER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD)),
                                INITIAL_TOTAL_SUPPLY,
                                DECIMALS)
                        .array()),
                assertSuccess());

        // should successfully create fungible token without TokenKeys

        // should revert on missing expiry

        // should revert with autoRenewPeriod less than 2592000
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
                        .name(NAME)
                        .symbol(SYMBOL)
                        .treasuryAccountId(OWNER_ID)
                        .memo(MEMO)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .maxSupply(10L)
                        .totalSupply(10L)
                        .build());
        return tokens;
    }
}
