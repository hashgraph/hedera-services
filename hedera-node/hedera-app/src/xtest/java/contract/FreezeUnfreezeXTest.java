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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER;
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
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.assertSuccess;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class FreezeUnfreezeXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        // ASSOCIATE
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(AssociationsTranslator.ASSOCIATE_ONE
                        .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, A_TOKEN_ADDRESS)
                        .array()),
                output -> assertSuccess());
        // FREEZE
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.FREEZE
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertSuccess());
        // TRY TRANSFER
        runHtsCallAndExpectRevert(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER.encodeCallWithArgs(RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        A_TOKEN_ID),
                FAIL_INVALID);
        // UNFREEZE
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(FreezeUnfreezeTranslator.UNFREEZE
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS)
                        .array()),
                output -> assertSuccess());
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
}
