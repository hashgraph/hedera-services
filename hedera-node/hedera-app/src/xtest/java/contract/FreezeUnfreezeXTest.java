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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER_FROM;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.AssociationsXTestConstants.B_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.B_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_BESU_ADDRESS;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.RECEIVER_ID;
import static contract.XTestConstants.assertSuccess;
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
import com.hedera.node.app.spi.state.ReadableKVState;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

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
 *     <li>Freeze {@code ERC721_TOKEN} without provided freeze key via {@link FreezeUnfreezeTranslator#FREEZE}. This should fail with code TOKEN_HAS_NO_FREEZE_KEY.</li>
 * </ol>
 */
public class FreezeUnfreezeXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        // ASSOCIATE
        runHtsCallAndExpectOnSuccess(
                RECEIVER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.ASSOCIATE_ONE
                        .encodeCallWithArgs(RECEIVER_HEADLONG_ADDRESS, A_TOKEN_ADDRESS)
                        .array()),
                assertSuccess());
        // FREEZE INVALID ACCOUNT
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_ACCOUNT_ID).array()), output));
        // FREEZE INVALID TOKEN
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));
        // FREEZE
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS)
                        .array()),
                assertSuccess());
        // TRY TRANSFER AND EXPECT FAIL
        runHtsCallAndExpectRevert(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER.encodeCallWithArgs(RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        A_TOKEN_ID),
                ACCOUNT_FROZEN_FOR_TOKEN);
        // UNFREEZE INVALID ACCOUNT
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_ACCOUNT_ID).array()), output));
        // UNFREEZE INVALID TOKEN
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));
        // UNFREEZE
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS)
                        .array()),
                assertSuccess());
        // TRY TRANSFER AND EXPECT SUCCESS
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER.encodeCallWithArgs(RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        A_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true)), output));
        // FREEZE NO FREEZE KEY
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(B_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(TOKEN_HAS_NO_FREEZE_KEY).array()),
                        output));
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
            }
        };
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        return new HashMap<>() {
            {
                put(
                        OWNER_ID,
                        Account.newBuilder()
                                .accountId(OWNER_ID)
                                .alias(OWNER_ADDRESS)
                                .tinybarBalance(100_000_000L)
                                .build());
                put(RECEIVER_ID, Account.newBuilder().accountId(RECEIVER_ID).build());
            }
        };
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        return new HashMap<>() {
            {
                put(
                        A_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(A_TOKEN_ID)
                                .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .freezeKey(AN_ED25519_KEY)
                                .build());
                put(
                        B_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(B_TOKEN_ID)
                                .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .build());
            }
        };
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, OWNER_ID, 1_000L);
        return tokenRelationships;
    }

    @Override
    protected void assertExpectedTokenRelations(@NotNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        assertTokenBalance(tokenRels, OWNER_ID, 900L);
        assertTokenBalance(tokenRels, RECEIVER_ID, 100L);
    }

    private void addErc20Relation(
            final Map<EntityIDPair, TokenRelation> tokenRelationships, final AccountID accountID, final long balance) {
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .accountId(accountID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(A_TOKEN_ID)
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
                                .tokenId(A_TOKEN_ID)
                                .accountId(accountID)
                                .build()))
                        .balance());
    }
}
