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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.AssociationsXTestConstants.B_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.B_TOKEN_ID;
import static contract.AssociationsXTestConstants.C_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.C_TOKEN_ID;
import static contract.AssociationsXTestConstants.D_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.D_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.INVALID_TOKEN_ADDRESS;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.delete.DeleteTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeTranslator;
import com.swirlds.platform.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises delete on a fungible and non-fungible token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Deletes {@code ERC20_TOKEN} via DELETE operation</li>
 *     <li>Deletes {@code ERC721_TOKEN} via DELETE operation</li>
 *     <li>Freezes a deleted {@code ERC20_TOKEN}. This should fail with TOKEN_WAS_DELETED</li>
 *     <li>Freezes a deleted {@code ERC721_TOKEN}. This should fail with TOKEN_WAS_DELETED</li>
 *     <li>Deletes {@code ERC20_TOKEN} without admin key. This should fail with TOKEN_IS_IMMUTABLE</li>
 *     <li>Deletes {@code ERC721_TOKEN} without admin key. This should fail with TOKEN_IS_IMMUTABLE</li>
 *     <li>Deletes {@code ERC20_TOKEN} with wrong admin key. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Deletes {@code ERC721_TOKEN} with wrong admin key. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Deletes token with invalid token address via DELETE operation. This should fail with INVALID_TOKEN_ID</li>
 * </ol>
 */
public class DeleteXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        // Successfully delete token
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(DeleteTranslator.DELETE_TOKEN
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS)
                        .array()),
                assertSuccess());

        // Successfully delete token
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(DeleteTranslator.DELETE_TOKEN
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS)
                        .array()),
                assertSuccess());

        // Try to freeze deleted token
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, asHeadlongAddress(SENDER_ADDRESS.toByteArray()))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_WAS_DELETED).array()), output));

        // Try to freeze deleted token
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, asHeadlongAddress(SENDER_ADDRESS.toByteArray()))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_WAS_DELETED).array()), output));

        // Fail if token has no admin key
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(DeleteTranslator.DELETE_TOKEN
                        .encodeCallWithArgs(A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_IS_IMMUTABLE).array()), output));

        // Fail if token has no admin key
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(DeleteTranslator.DELETE_TOKEN
                        .encodeCallWithArgs(B_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_IS_IMMUTABLE).array()), output));

        // Fail if token has wrong admin key
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(DeleteTranslator.DELETE_TOKEN
                        .encodeCallWithArgs(C_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output));

        // Fail if token has wrong admin key
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(DeleteTranslator.DELETE_TOKEN
                        .encodeCallWithArgs(D_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output));

        // should fail when token has invalid address
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(DeleteTranslator.DELETE_TOKEN
                        .encodeCallWithArgs(INVALID_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));
    }

    @Override
    protected void assertExpectedTokens(@NonNull ReadableKVState<TokenID, Token> tokens) {
        final var erc20Token = tokens.get(ERC20_TOKEN_ID);
        assertNotNull(erc20Token);
        assertTrue(erc20Token.deleted());

        final var erc721Token = tokens.get(ERC721_TOKEN_ID);
        assertNotNull(erc721Token);
        assertFalse(erc721Token.deleted());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return withSenderAddress(new HashMap<>());
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
                        .adminKey(SENDER_CONTRACT_ID_KEY)
                        .freezeKey(SENDER_CONTRACT_ID_KEY)
                        .build());
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .treasuryAccountId(SENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .adminKey(SENDER_CONTRACT_ID_KEY)
                        .freezeKey(SENDER_CONTRACT_ID_KEY)
                        .build());
        tokens.put(
                A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .treasuryAccountId(SENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(B_TOKEN_ID)
                        .treasuryAccountId(SENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                C_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(C_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .adminKey(AN_ED25519_KEY)
                        .build());
        tokens.put(
                D_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(D_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .adminKey(AN_ED25519_KEY)
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
                        .build());
        return accounts;
    }
}
