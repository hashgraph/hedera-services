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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.INT;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.AssociationsXTestConstants.B_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.B_TOKEN_ID;
import static contract.AssociationsXTestConstants.C_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.C_TOKEN_ID;
import static contract.AssociationsXTestConstants.D_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.D_TOKEN_ID;
import static contract.AssociationsXTestConstants.E_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsTranslator;
import com.swirlds.state.spi.ReadableKVState;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

/**
 * Exercises both classic and HRC associations via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Associates {@code A_TOKEN} via {@link AssociationsTranslator#ASSOCIATE_ONE}.</li>
 *     <li>Associates {@code B_TOKEN} and {@code C_TOKEN} via {@link AssociationsTranslator#ASSOCIATE_MANY}.</li>
 *     <li>Dissociates {@code B_TOKEN} via {@link AssociationsTranslator#HRC_DISSOCIATE}.</li>
 *     <li>Associates {@code D_TOKEN} via {@link AssociationsTranslator#HRC_ASSOCIATE}.</li>
 *     <li>Associates {@code E_TOKEN} via {@link AssociationsTranslator#HRC_ASSOCIATE}.</li>
 *     <li>Dissociates {@code C_TOKEN} and {@code D_TOKEN} via {@link AssociationsTranslator#DISSOCIATE_MANY}.</li>
 * </ol>
 * So the final associated tokens are just {@code A_TOKEN} and {@code E_TOKEN}.
 */
public class AssociationsXTest extends AbstractContractXTest {
    private static final TupleType HRC_ENCODER = TupleType.parse(INT);

    @Override
    protected void doScenarioOperations() {
        // ASSOCIATE_ONE (+A_TOKEN)
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.ASSOCIATE_ONE
                        .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
        // ASSOCIATE_MANY (+B_TOKEN, +C_TOKEN)
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.ASSOCIATE_MANY
                        .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, new Address[] {B_TOKEN_ADDRESS, C_TOKEN_ADDRESS})
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
        // DISSOCIATE_ONE (-B_TOKEN)
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.DISSOCIATE_ONE
                        .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, B_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
        // HRC_ASSOCIATE (+D_TOKEN)
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(AssociationsTranslator.HRC_ASSOCIATE.encodeCallWithArgs(), D_TOKEN_ID),
                output -> assertEquals(
                        Bytes.wrap(HRC_ENCODER
                                .encodeElements(BigInteger.valueOf(SUCCESS.protoOrdinal()))
                                .array()),
                        output));
        // HRC_ASSOCIATE (+E_TOKEN)
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(AssociationsTranslator.HRC_ASSOCIATE.encodeCallWithArgs(), E_TOKEN_ID),
                output -> assertEquals(
                        Bytes.wrap(HRC_ENCODER
                                .encodeElements(BigInteger.valueOf(SUCCESS.protoOrdinal()))
                                .array()),
                        output));
        // DISSOCIATE_MANY (-C_TOKEN, -D_TOKEN)
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.DISSOCIATE_MANY
                        .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, new Address[] {C_TOKEN_ADDRESS, D_TOKEN_ADDRESS})
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
    }

    @Override
    protected void assertExpectedTokenRelations(@NotNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        // The owner should be associated with A_TOKEN and E_TOKEN
        assertPresentTokenAssociation(tokenRels, OWNER_ID, A_TOKEN_ID);
        assertMissingTokenAssociation(tokenRels, OWNER_ID, B_TOKEN_ID);
        assertMissingTokenAssociation(tokenRels, OWNER_ID, C_TOKEN_ID);
        assertMissingTokenAssociation(tokenRels, OWNER_ID, D_TOKEN_ID);
        assertPresentTokenAssociation(tokenRels, OWNER_ID, E_TOKEN_ID);
    }

    private void assertMissingTokenAssociation(
            @NotNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            @NotNull final AccountID accountId,
            @NotNull final TokenID tokenId) {
        assertTokenAssociation(tokenRels, accountId, tokenId, false);
    }

    private void assertPresentTokenAssociation(
            @NotNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            @NotNull final AccountID accountId,
            @NotNull final TokenID tokenId) {
        assertTokenAssociation(tokenRels, accountId, tokenId, true);
    }

    private void assertTokenAssociation(
            @NotNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            @NotNull final AccountID accountId,
            @NotNull final TokenID tokenId,
            final boolean shouldBePresent) {
        final var tokenRel = tokenRels.get(
                EntityIDPair.newBuilder().tokenId(tokenId).accountId(accountId).build());
        if (shouldBePresent) {
            assertNotNull(tokenRel);
        } else {
            assertNull(tokenRel);
        }
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(B_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                C_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(C_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                D_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(D_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                E_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(E_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        return tokens;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(OWNER_ADDRESS)
                        .key(AN_ED25519_KEY)
                        .tinybarBalance(100_000_000L)
                        .build());
        return accounts;
    }
}
