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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
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
import com.hedera.node.app.spi.state.ReadableKVState;
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                XTestConstants.assertSuccess("V1 update failed"));

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.OWNER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V1
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                XTestConstants.assertSuccess("V2 update failed"));

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.OWNER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V2
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
                                        "memo",
                                        true,
                                        1000L,
                                        false,
                                        // TokenKey
                                        new Tuple[] {},
                                        // Expiry
                                        Tuple.of(0L, asAddress(""), 0L)))
                        .array()),
                XTestConstants.assertSuccess("V3 update failed"));

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.OWNER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateTranslator.TOKEN_UPDATE_INFO_FUNCTION_V3
                        .encodeCallWithArgs(
                                MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                                Tuple.of(
                                        NEW_NAME,
                                        NEW_SYMBOL,
                                        asHeadlongAddress(XTestConstants.SENDER_ADDRESS.toByteArray()),
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
        final var erc20Token = tokens.get(XTestConstants.ERC20_TOKEN_ID);
        assertNotNull(erc20Token);
        assertEquals(NEW_NAME, erc20Token.name());
        assertEquals(NEW_SYMBOL, erc20Token.symbol());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.SENDER_ADDRESS).build(), XTestConstants.SENDER_ID);
        aliases.put(
                ProtoBytes.newBuilder()
                        .value(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS)
                        .build(),
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                XTestConstants.ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .treasuryAccountId(XTestConstants.SENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(XTestConstants.AN_ED25519_KEY)
                        .adminKey(XTestConstants.SENDER_CONTRACT_ID_KEY)
                        .autoRenewAccountId(XTestConstants.SENDER_ID)
                        .build());
        tokens.put(
                AssociationsXTestConstants.A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(Scenarios.ALICE.account().key())
                        .adminKey(Scenarios.ALICE.account().key())
                        .build());
        tokens.put(
                AssociationsXTestConstants.B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.B_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc20Relation(tokenRelationships, XTestConstants.SENDER_ID, 0);
        return tokenRelationships;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                XTestConstants.SENDER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.OWNER_ID)
                        .alias(XTestConstants.SENDER_ADDRESS)
                        .key(XTestConstants.SENDER_CONTRACT_ID_KEY)
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
