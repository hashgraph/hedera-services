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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.INT;
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
import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

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
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.ASSOCIATE_ONE
                        .encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS, AssociationsXTestConstants.A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
        // ASSOCIATE_MANY (+B_TOKEN, +C_TOKEN)
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.ASSOCIATE_MANY
                        .encodeCallWithArgs(XTestConstants.OWNER_HEADLONG_ADDRESS, new Address[] {
                            AssociationsXTestConstants.B_TOKEN_ADDRESS, AssociationsXTestConstants.C_TOKEN_ADDRESS
                        })
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
        // DISSOCIATE_ONE (-B_TOKEN)
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.DISSOCIATE_ONE
                        .encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS, AssociationsXTestConstants.B_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
        // HRC_ASSOCIATE (+D_TOKEN)
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        AssociationsTranslator.HRC_ASSOCIATE.encodeCallWithArgs(),
                        AssociationsXTestConstants.D_TOKEN_ID),
                output -> assertEquals(
                        Bytes.wrap(HRC_ENCODER
                                .encodeElements(BigInteger.valueOf(SUCCESS.protoOrdinal()))
                                .array()),
                        output));
        // HRC_ASSOCIATE (+E_TOKEN)
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        AssociationsTranslator.HRC_ASSOCIATE.encodeCallWithArgs(),
                        AssociationsXTestConstants.E_TOKEN_ID),
                output -> assertEquals(
                        Bytes.wrap(HRC_ENCODER
                                .encodeElements(BigInteger.valueOf(SUCCESS.protoOrdinal()))
                                .array()),
                        output));
        // DISSOCIATE_MANY (-C_TOKEN, -D_TOKEN)
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.DISSOCIATE_MANY
                        .encodeCallWithArgs(XTestConstants.OWNER_HEADLONG_ADDRESS, new Address[] {
                            AssociationsXTestConstants.C_TOKEN_ADDRESS, AssociationsXTestConstants.D_TOKEN_ADDRESS
                        })
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
    }

    @Override
    protected void assertExpectedTokenRelations(@NonNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        // The owner should be associated with A_TOKEN and E_TOKEN
        assertPresentTokenAssociation(tokenRels, XTestConstants.OWNER_ID, AssociationsXTestConstants.A_TOKEN_ID);
        assertMissingTokenAssociation(tokenRels, XTestConstants.OWNER_ID, AssociationsXTestConstants.B_TOKEN_ID);
        assertMissingTokenAssociation(tokenRels, XTestConstants.OWNER_ID, AssociationsXTestConstants.C_TOKEN_ID);
        assertMissingTokenAssociation(tokenRels, XTestConstants.OWNER_ID, AssociationsXTestConstants.D_TOKEN_ID);
        assertPresentTokenAssociation(tokenRels, XTestConstants.OWNER_ID, AssociationsXTestConstants.E_TOKEN_ID);
    }

    private void assertMissingTokenAssociation(
            @NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId) {
        assertTokenAssociation(tokenRels, accountId, tokenId, false);
    }

    private void assertPresentTokenAssociation(
            @NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId) {
        assertTokenAssociation(tokenRels, accountId, tokenId, true);
    }

    private void assertTokenAssociation(
            @NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId,
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
                AssociationsXTestConstants.A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                AssociationsXTestConstants.B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.B_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                AssociationsXTestConstants.C_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.C_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                AssociationsXTestConstants.D_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.D_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                AssociationsXTestConstants.E_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.E_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        return tokens;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                XTestConstants.OWNER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.OWNER_ID)
                        .alias(XTestConstants.OWNER_ADDRESS)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .tinybarBalance(100_000_000L)
                        .build());
        return accounts;
    }
}
