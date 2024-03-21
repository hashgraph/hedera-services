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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.INVALID_TOKEN_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_HEADLONG_ADDRESS;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.update.UpdateExpiryTranslator;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.swirlds.platform.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises update a token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V1}.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V1}. This should fail with code INVALID_SIGNATURE.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V1}. This should fail with code INVALID_EXPIRATION_TIME.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V1}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V1}. This should fail with code INVALID_TOKEN_ID.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V2}.
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V2}. This should fail with code INVALID_SIGNATURE.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V2}. This should fail with code INVALID_EXPIRATION_TIME.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V2}. This should fail with code INVALID_RENEWAL_PERIOD.</li>
 *     <li>Update expiry {@code ERC20_TOKEN} via {@link UpdateExpiryTranslator#UPDATE_TOKEN_EXPIRY_INFO_V2}. This should fail with code INVALID_TOKEN_ID.</li>
 * </ol>
 */
public class UpdatesExpiryXTest extends AbstractContractXTest {
    private static final long EXPIRY_TIMESTAMP = Instant.now().plusSeconds(3600).toEpochMilli() / 1000;
    private static final long AUTO_RENEW_PERIOD = 8_000_000L;

    @Override
    protected void doScenarioOperations() {
        // Successfully update token via UPDATE_TOKEN_EXPIRY_INFO_V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V1
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD))
                        .array()),
                assertSuccess());

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V1
                        .encodeCallWithArgs(
                                A_TOKEN_ADDRESS, Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_SIGNATURE).array()), output, "Wrong key"));

        // Fails if the expiration time is invalid
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V1
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, Tuple.of(123456L, asAddress(""), 0L))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(INVALID_EXPIRATION_TIME).array()),
                        output));

        // Fails if the renewal period is invalid
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V1
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS, Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, 1000L))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_RENEWAL_PERIOD).array()), output));

        // should fail when token has invalid address
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V1
                        .encodeCallWithArgs(
                                INVALID_TOKEN_ADDRESS,
                                Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));

        // Successfully update token via UPDATE_TOKEN_EXPIRY_INFO_V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V2
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS,
                                Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD))
                        .array()),
                assertSuccess());

        // Should throw `INVALID_SIGNATURE`
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V2
                        .encodeCallWithArgs(
                                A_TOKEN_ADDRESS, Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_SIGNATURE).array()), output, "Wrong key"));

        // Fails if the expiration time is invalid
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V2
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, Tuple.of(123456L, asAddress(""), 0L))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(
                                ReturnTypes.encodedRc(INVALID_EXPIRATION_TIME).array()),
                        output));

        // Fails if the renewal period is invalid
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V2
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS, Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, 1000L))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_RENEWAL_PERIOD).array()), output));

        // should fail when token has invalid address
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(UpdateExpiryTranslator.UPDATE_TOKEN_EXPIRY_INFO_V2
                        .encodeCallWithArgs(
                                INVALID_TOKEN_ADDRESS,
                                Tuple.of(EXPIRY_TIMESTAMP, SENDER_HEADLONG_ADDRESS, AUTO_RENEW_PERIOD))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));
    }

    @Override
    protected void assertExpectedTokens(@NonNull ReadableKVState<TokenID, Token> tokens) {
        final var erc20Token = tokens.get(ERC20_TOKEN_ID);
        assertNotNull(erc20Token);
        assertEquals(EXPIRY_TIMESTAMP, erc20Token.expirationSecond());
        assertEquals(AUTO_RENEW_PERIOD, erc20Token.autoRenewSeconds());
        assertEquals(SENDER_ID, erc20Token.autoRenewAccountId());
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
        tokens.put(
                A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(Scenarios.ALICE.account().key())
                        .adminKey(Scenarios.ALICE.account().key())
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
