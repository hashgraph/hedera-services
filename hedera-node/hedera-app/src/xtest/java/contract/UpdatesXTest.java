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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.AssociationsXTestConstants.B_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateTranslator;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.swirlds.platform.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises update a token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V1}.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V1}. This should fail with code INVALID_SIGNATURE.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V1}. This should fail with code INVALID_TREASURY_ACCOUNT_FOR_TOKEN.</li>
 *  *  <li>Update {@code ERC721_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V1}. This should fail with code INVALID_TOKEN_ID.</li>
 *  *  <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V1}. This should fail with code INVALID_EXPIRATION_TIME.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V2}.
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V2}. This should fail with code INVALID_SIGNATURE.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V2}. This should fail with code INVALID_TREASURY_ACCOUNT_FOR_TOKEN.</li>
 *     <li>Update {@code ERC721_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V2}. This should fail with code INVALID_TOKEN_ID.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V2}. This should fail with code INVALID_EXPIRATION_TIME.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V3}.
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V3}. This should fail with code INVALID_SIGNATURE.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V3}. This should fail with code INVALID_TREASURY_ACCOUNT_FOR_TOKEN.</li>
 *     <li>Update {@code ERC721_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V3}. This should fail with code INVALID_TOKEN_ID.</li>
 *     <li>Update {@code ERC20_TOKEN} via {@link UpdateTranslator#TOKEN_UPDATE_INFO_FUNCTION_V3}. This should fail with code INVALID_EXPIRATION_TIME.</li>
 * </ol>
 */
public class UpdatesXTest extends AbstractContractXTest {
    private static final String NEW_NAME = "New name";
    private static final String NEW_SYMBOL = "New symbol";

    @Override
    protected void doScenarioOperations() {
        // Successfully update token via TOKEN_UPDATE_INFO V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                assertSuccess("V1 update failed"));

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                A_TOKEN_ADDRESS,
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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_SIGNATURE).array()), output, "Wrong key"));

        // Fails if the treasury is invalid (owner address is not initialized)
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(OWNER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
                                .array()),
                        output,
                        "Invalid treasury account not detected"));

        // Fails if the token ID is invalid (erc721 token address is not initialized)
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()),
                        output,
                        "Invalid token ID not detected"));

        // Fails if the expiration time is invalid
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
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
                                        Tuple.of(123456L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(INVALID_EXPIRATION_TIME).array()),
                        output,
                        "Invalid expiration time not detected"));

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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                assertSuccess("V2 update failed"));

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                A_TOKEN_ADDRESS,
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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_SIGNATURE).array()), output, "Wrong key"));

        // Fails if the treasury is invalid (owner address is not initialized)
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(OWNER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
                                .array()),
                        output,
                        "Invalid treasury account not detected"));

        // Fails if the token ID is invalid (erc721 token address is not initialized)
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()),
                        output,
                        "Invalid token ID not detected"));

        // Fails if the expiration time is invalid
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
                                        Tuple.of(123456L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(INVALID_EXPIRATION_TIME).array()),
                        output,
                        "Invalid expiration time not detected"));

        // Successfully update token via TOKEN_UPDATE_INFO V3
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
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                assertSuccess("V3 update failed"));

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                A_TOKEN_ADDRESS,
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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_SIGNATURE).array()), output, "Wrong key"));

        // Fails if the treasury is invalid (owner address is not initialized)
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(OWNER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TREASURY_ACCOUNT_FOR_TOKEN)
                                .array()),
                        output,
                        "Invalid treasury account not detected"));

        // Fails if the token ID is invalid (erc721 token address is not initialized)
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
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
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()),
                        output,
                        "Invalid token ID not detected"));

        // Fails if the expiration time is invalid
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
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(123456L, asAddress(""), 0L)))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(INVALID_EXPIRATION_TIME).array()),
                        output,
                        "Invalid expiration time not detected"));
    }

    @Override
    protected void assertExpectedTokens(@NonNull ReadableKVState<TokenID, Token> tokens) {
        final var erc20Token = tokens.get(ERC20_TOKEN_ID);
        assertNotNull(erc20Token);
        assertEquals(NEW_NAME, erc20Token.name());
        assertEquals(NEW_SYMBOL, erc20Token.symbol());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(UNAUTHORIZED_SPENDER_ADDRESS).build(), UNAUTHORIZED_SPENDER_ID);
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
        tokens.put(
                A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(Scenarios.ALICE.account().key())
                        .adminKey(Scenarios.ALICE.account().key())
                        .build());
        tokens.put(
                B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(B_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
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

    private static Address asAddress(String address) {
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        final var addressAsInteger = addressBytes.toUnsignedBigInteger();
        return Address.wrap(Address.toChecksumAddress(addressAsInteger));
    }
}
