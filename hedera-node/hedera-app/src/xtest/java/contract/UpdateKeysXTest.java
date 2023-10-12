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

import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.assertSuccess;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateKeysTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class UpdateKeysXTest extends AbstractContractXTest {
    private final Tuple[] tokenKey = new Tuple[] {
        Tuple.of(
                BigInteger.valueOf(1),
                Tuple.of(false, SENDER_HEADLONG_ADDRESS, new byte[] {}, new byte[] {}, asAddress("")))
    };
    private final Tuple[] invalidKey = new Tuple[] {
        Tuple.of(
                BigInteger.valueOf(1),
                Tuple.of(false, SENDER_HEADLONG_ADDRESS, new byte[] {}, new byte[] {}, SENDER_HEADLONG_ADDRESS))
    };

    @Override
    protected void doScenarioOperations() {
        // Update token keys
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateKeysTranslator.TOKEN_UPDATE_KEYS_FUNCTION
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, tokenKey)
                        .array()),
                assertSuccess());

        /*runHtsCallAndExpectOnSuccess(
        SENDER_BESU_ADDRESS,
        Bytes.wrap(UpdateKeysTranslator.TOKEN_UPDATE_KEYS_FUNCTION
                .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, invalidKey)
                .array()),
        output -> assertEquals(
                Bytes.wrap(
                        ReturnTypes.encodedRc(INVALID_TRANSACTION_BODY).array()),
                output));*/
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
        return accounts;
    }

    // casts Address to null
    public static Address asAddress(String address) {
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }
}
