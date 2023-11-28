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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER_FROM;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeTranslator;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises freeze and unfreeze a token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Associate {@code ERC20_TOKEN} to RECEIVER.</li>
 *     <li>Freeze {@code ERC20TOKEN} via {@link FreezeUnfreezeTranslator#FREEZE}. This should fail with code INVALID_ACCOUNT_ID.</li>
 *     <li>Freeze {@code ERC20TOKEN} via {@link FreezeUnfreezeTranslator#FREEZE}. This should fail with code INVALID_TOKEN_ID.</li>
 *     <li>Freeze {@code ERC2-TOKEN} via {@link FreezeUnfreezeTranslator#FREEZE}.</li>
 *     <li>Transfer {@code ERC20_TOKEN} from SENDER to RECEIVER.  This should fail with code ACCOUNT_FROZEN_FOR_TOKEN.</li>
 *     <li>Unfreeze {@code ERC20_TOKEN} via {@link FreezeUnfreezeTranslator#UNFREEZE}. This should fail with code INVALID_ACCOUNT_ID.</li>
 *     <li>Unfreeze {@code ERC20_TOKEN} via {@link FreezeUnfreezeTranslator#UNFREEZE}. This should fail with code INVALID_TOKEN_ID.</li>
 *     <li>Unfreeze {@code ERC20_TOKEN} via {@link FreezeUnfreezeTranslator#UNFREEZE}.</li>
 *     <li>Transfer {@code ERC20_TOKEN} from  SENDER to RECEIVER.  This should now succeed</li>
 *     <li>Freeze {@code ERC20_TOKEN} without provided freeze key via {@link FreezeUnfreezeTranslator#FREEZE}. This should fail with code TOKEN_HAS_NO_FREEZE_KEY.</li>
 *     <li>Unfreeze {@code ERC20_TOKEN} without provided freeze key via {@link FreezeUnfreezeTranslator#UNFREEZE}. This should fail with code TOKEN_HAS_NO_FREEZE_KEY.</li>
 *     <li>Unfreeze {@code ERC20_TOKEN} with different freeze key via {@link FreezeUnfreezeTranslator#UNFREEZE}. This should fail with code INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.</li>
 *     <li>Freeze {@code ERC20_TOKEN} with different freeze key via {@link FreezeUnfreezeTranslator#FREEZE}. This should fail with code INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.</li>
 * </ol>
 */
public class FreezeUnfreezeXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        // ASSOCIATE
        runHtsCallAndExpectOnSuccess(
                XTestConstants.RECEIVER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.ASSOCIATE_ONE
                        .encodeCallWithArgs(
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS, AssociationsXTestConstants.A_TOKEN_ADDRESS)
                        .array()),
                XTestConstants.assertSuccess());
        // FREEZE INVALID ACCOUNT
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS, AssociationsXTestConstants.A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_ACCOUNT_ID).array()), output));
        // FREEZE INVALID TOKEN
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));
        // FREEZE
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                XTestConstants.assertSuccess());
        // TRY TRANSFER AND EXPECT FAIL
        runHtsCallAndExpectRevert(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER.encodeCallWithArgs(
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        AssociationsXTestConstants.A_TOKEN_ID),
                ACCOUNT_FROZEN_FOR_TOKEN);
        // UNFREEZE INVALID ACCOUNT
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS, AssociationsXTestConstants.A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_ACCOUNT_ID).array()), output));
        // UNFREEZE INVALID TOKEN
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));
        // UNFREEZE
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                XTestConstants.assertSuccess());
        // TRY TRANSFER AND EXPECT SUCCESS
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER.encodeCallWithArgs(
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        AssociationsXTestConstants.A_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true)), output));
        // FREEZE NO FREEZE KEY
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.B_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(TOKEN_HAS_NO_FREEZE_KEY).array()),
                        output));
        // UNFREEZE NO FREEZE KEY
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.B_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(TOKEN_HAS_NO_FREEZE_KEY).array()),
                        output));
        // UNFREEZE DIFFERENT FREEZE KEY
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.C_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output));
        // FREEZE DIFFERENT FREEZE KEY
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.C_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output));
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
            }
        };
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        return new HashMap<>() {
            {
                put(
                        XTestConstants.OWNER_ID,
                        Account.newBuilder()
                                .accountId(XTestConstants.OWNER_ID)
                                .alias(XTestConstants.OWNER_ADDRESS)
                                .key(XTestConstants.AN_ED25519_KEY)
                                .tinybarBalance(100_000_000L)
                                .build());
                put(
                        XTestConstants.RECEIVER_ID,
                        Account.newBuilder()
                                .accountId(XTestConstants.RECEIVER_ID)
                                .key(XTestConstants.AN_ED25519_KEY)
                                .build());
            }
        };
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        return new HashMap<>() {
            {
                put(
                        AssociationsXTestConstants.A_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                                .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .freezeKey(XTestConstants.AN_ED25519_KEY)
                                .build());
                put(
                        AssociationsXTestConstants.B_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(AssociationsXTestConstants.B_TOKEN_ID)
                                .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .build());
                put(
                        AssociationsXTestConstants.C_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(AssociationsXTestConstants.C_TOKEN_ID)
                                .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .freezeKey(Scenarios.BOB.account().key())
                                .build());
            }
        };
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, XTestConstants.OWNER_ID, 1_000L);
        return tokenRelationships;
    }

    @Override
    protected void assertExpectedTokenRelations(@NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        assertTokenBalance(tokenRels, XTestConstants.OWNER_ID, 900L);
        assertTokenBalance(tokenRels, XTestConstants.RECEIVER_ID, 100L);
    }

    private void addErc20Relation(
            final Map<EntityIDPair, TokenRelation> tokenRelationships, final AccountID accountID, final long balance) {
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                        .accountId(accountID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                        .accountId(accountID)
                        .balance(balance)
                        .kycGranted(true)
                        .build());
    }

    private void assertTokenBalance(
            final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            final AccountID accountID,
            final long expectedBalance) {
        assertEquals(
                expectedBalance,
                Objects.requireNonNull(tokenRels.get(EntityIDPair.newBuilder()
                                .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                                .accountId(accountID)
                                .build()))
                        .balance());
    }
}
