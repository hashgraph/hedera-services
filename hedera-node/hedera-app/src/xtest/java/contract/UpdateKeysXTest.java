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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.INVALID_ACCOUNT_ADDRESS;
import static contract.XTestConstants.INVALID_ACCOUNT_HEADLONG_ADDRESS;
import static contract.XTestConstants.INVALID_CONTRACT_ID_KEY;
import static contract.XTestConstants.INVALID_ID;
import static contract.XTestConstants.INVALID_TOKEN_ADDRESS;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateKeysTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class UpdateKeysXTest extends AbstractContractXTest {
    private final Tuple[] TOKEN_KEY = new Tuple[] {
        Tuple.of(
                BigInteger.valueOf(1),
                Tuple.of(false, SENDER_HEADLONG_ADDRESS, new byte[] {}, new byte[] {}, asAddress("")))
    };
    private final Tuple[] INVALID_TOKEN_KEY = new Tuple[] {
        Tuple.of(
                BigInteger.valueOf(1),
                Tuple.of(false, asAddress(""), new byte[] {}, new byte[] {}, INVALID_ACCOUNT_HEADLONG_ADDRESS))
    };

    @Override
    protected void doScenarioOperations() {
        // Update token keys
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateKeysTranslator.TOKEN_UPDATE_KEYS_FUNCTION
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, TOKEN_KEY)
                        .array()),
                assertSuccess());
        // Should throw `INVALID_ADMIN_KEY` as we are passing an invalid key with key type admin key
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateKeysTranslator.TOKEN_UPDATE_KEYS_FUNCTION
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, INVALID_TOKEN_KEY)
                        .array()),
                output -> {
                    assertEquals(
                            Bytes.wrap(ReturnTypes.encodedRc(INVALID_ADMIN_KEY).array()), output);
                });
        // Should throw `INVALID_TOKEN_ID` as we are passing an invalid token address
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateKeysTranslator.TOKEN_UPDATE_KEYS_FUNCTION
                        .encodeCallWithArgs(INVALID_TOKEN_ADDRESS, INVALID_TOKEN_KEY)
                        .array()),
                output -> {
                    assertEquals(
                            Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output);
                });
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(INVALID_ACCOUNT_ADDRESS).build(), INVALID_ID);
        return aliases;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, SENDER_ID, 0);
        addErc20Relation(tokenRelationships, INVALID_ID, 0);
        return tokenRelationships;
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

        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .treasuryAccountId(INVALID_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .adminKey(INVALID_CONTRACT_ID_KEY)
                        .autoRenewAccountId(INVALID_ID)
                        .build());
        return tokens;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(SENDER_ID)
                        .alias(SENDER_ADDRESS)
                        .key(SENDER_CONTRACT_ID_KEY)
                        .smartContract(true)
                        .build());
        accounts.put(
                INVALID_ID,
                Account.newBuilder()
                        .accountId(INVALID_ID)
                        .alias(INVALID_ACCOUNT_ADDRESS)
                        .key(INVALID_CONTRACT_ID_KEY)
                        .smartContract(true)
                        .build());
        return accounts;
    }

    // casts Address to null
    public static Address asAddress(String address) {
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }
}
