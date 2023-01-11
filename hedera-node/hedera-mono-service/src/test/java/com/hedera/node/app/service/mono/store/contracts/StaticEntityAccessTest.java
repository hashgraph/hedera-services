/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts;

import static com.hedera.node.app.service.mono.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.Type.CONTRACT_BYTECODE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.test.utils.TxnUtils.assertFailsRevertingWith;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.enums.TokenType;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.commons.codec.DecoderException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaticEntityAccessTest {
    @Mock private OptionValidator validator;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private HederaAccountCustomizer customizer;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private AccountStorageAdapter accounts;
    @Mock private VirtualMap<ContractKey, IterableContractValue> storage;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> blobs;
    @Mock private TokenRelStorageAdapter tokenAssociations;
    @Mock private MerkleMap<EntityNumPair, MerkleUniqueToken> nfts;
    @Mock private TokenInfo tokenInfo;
    @Mock private TokenNftInfo tokenNftInfo;
    @Mock private List<CustomFee> customFees;

    private StaticEntityAccess subject;

    private static final JKey key = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());

    private final AccountID id = IdUtils.asAccount("0.0.1234");
    private final AccountID nonExtantId = IdUtils.asAccount("0.0.1235");
    private final UInt256 uint256Key = UInt256.ONE;
    private final Bytes bytesKey = uint256Key.toBytes();
    private final ContractKey contractKey =
            new ContractKey(id.getAccountNum(), uint256Key.toArray());
    private final VirtualBlobKey blobKey =
            new VirtualBlobKey(CONTRACT_BYTECODE, (int) id.getAccountNum());
    private final IterableContractValue contractVal = new IterableContractValue(BigInteger.ONE);
    private final VirtualBlobValue blobVal = new VirtualBlobValue("data".getBytes());
    private static final ByteString pretendAlias =
            ByteString.copyFrom(unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb"));

    private final MerkleAccount someNonContractAccount =
            (MerkleAccount)
                    new HederaAccountCustomizer()
                            .isReceiverSigRequired(false)
                            .key(key)
                            .proxy(MISSING_ENTITY_ID)
                            .isDeleted(false)
                            .expiry(someExpiry)
                            .memo("")
                            .isSmartContract(false)
                            .autoRenewPeriod(1234L)
                            .customizing(new MerkleAccount());
    private final MerkleAccount someContractAccount =
            (MerkleAccount)
                    new HederaAccountCustomizer()
                            .isReceiverSigRequired(false)
                            .alias(pretendAlias)
                            .key(key)
                            .proxy(MISSING_ENTITY_ID)
                            .isDeleted(false)
                            .expiry(someExpiry)
                            .memo("")
                            .isSmartContract(true)
                            .autoRenewPeriod(1234L)
                            .customizing(new MerkleAccount());

    @BeforeEach
    void setUp() {
        given(stateView.tokens()).willReturn(tokens);
        given(stateView.storage()).willReturn(blobs);
        given(stateView.accounts()).willReturn(accounts);
        given(stateView.contractStorage()).willReturn(storage);
        given(stateView.tokenAssociations()).willReturn(tokenAssociations);
        given(stateView.uniqueTokens()).willReturn(UniqueTokenMapAdapter.wrap(nfts));

        subject = new StaticEntityAccess(stateView, aliases, validator);
    }

    @Test
    void worldLedgersJustIncludeAliases() {
        final var ledgers = subject.worldLedgers();
        assertSame(aliases, ledgers.aliases());
        assertNull(ledgers.accounts());
        assertNull(ledgers.tokenRels());
        assertNull(ledgers.tokens());
        assertNull(ledgers.nfts());
    }

    @Test
    void canGetAlias() {
        given(accounts.get(EntityNum.fromAccountId(id))).willReturn(someContractAccount);
        assertEquals(pretendAlias, subject.alias(asTypedEvmAddress(id)));
    }

    @Test
    void usableIfNonDeletedAndAttached() {
        given(
                        validator.expiryStatusGiven(
                                someNonContractAccount.getBalance(),
                                someNonContractAccount.isExpiredAndPendingRemoval(),
                                someNonContractAccount.isSmartContract()))
                .willReturn(OK);
        given(accounts.get(EntityNum.fromAccountId(id))).willReturn(someNonContractAccount);
        assertTrue(subject.isUsable(asTypedEvmAddress(id)));
    }

    @Test
    void mutatorsAndTransactionalSemanticsThrows() {
        assertThrows(UnsupportedOperationException.class, () -> subject.customize(id, customizer));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.putStorage(id, uint256Key, uint256Key));
        assertThrows(UnsupportedOperationException.class, () -> subject.storeCode(id, bytesKey));
        assertThrows(UnsupportedOperationException.class, () -> subject.currentManagedChangeSet());
        assertThrows(UnsupportedOperationException.class, () -> subject.recordNewKvUsageTo(null));
        assertThrows(UnsupportedOperationException.class, () -> subject.flushStorage(null));
        assertDoesNotThrow(subject::startAccess);
    }

    @Test
    void metadataOfNFT() {
        given(nfts.get(nftKey)).willReturn(treasuryOwned);
        final var actual = subject.metadataOf(nft);
        assertEquals("There, the eyes are", actual);
    }

    @Test
    void infoForToken() {
        given(stateView.infoForToken(tokenId)).willReturn(Optional.of(tokenInfo));

        final var tokenInfo = subject.infoForToken(tokenId);
        assertNotNull(tokenInfo.get());
    }

    @Test
    void infoForNftToken() {
        final var nftId =
                NftID.newBuilder()
                        .setTokenID(nft.tokenId())
                        .setSerialNumber(nft.serialNo())
                        .build();
        given(nfts.get(EntityNumPair.fromNftId(nft))).willReturn(treasuryOwned);
        given(stateView.infoForNft(nftId)).willReturn(Optional.of(tokenNftInfo));

        final var tokenNftInfo = subject.infoForNft(nftId);
        assertNotNull(tokenNftInfo.get());
    }

    @Test
    void infoForTokenCustomFees() {
        given(stateView.infoForTokenCustomFees(tokenId)).willReturn(customFees);

        final var customFees = subject.infoForTokenCustomFees(tokenId);
        assertNotNull(customFees);
    }

    @Test
    void nonMutatorsWork() {
        given(accounts.get(EntityNum.fromAccountId(id))).willReturn(someNonContractAccount);
        given(accounts.get(EntityNum.fromAccountId(nonExtantId))).willReturn(null);
        given(stateView.tokenExists(HTSTestsUtil.fungible)).willReturn(true);

        assertEquals(
                someNonContractAccount.getBalance(), subject.getBalance(asTypedEvmAddress(id)));
        assertTrue(subject.isExtant(asTypedEvmAddress(id)));
        assertFalse(subject.isExtant(asTypedEvmAddress(nonExtantId)));
        assertTrue(subject.isTokenAccount(HTSTestsUtil.fungibleTokenAddr));
    }

    @Test
    void notUsableIfMissing() {
        assertFalse(subject.isUsable(asTypedEvmAddress(id)));
    }

    @Test
    void notUsableIfDeleted() {
        given(accounts.get(EntityNum.fromAccountId(id))).willReturn(someNonContractAccount);
        someNonContractAccount.setDeleted(true);
        assertFalse(subject.isUsable(asTypedEvmAddress(id)));
    }

    @Test
    void getWorks() {
        given(storage.get(contractKey)).willReturn(contractVal);

        final var uint256Val = subject.getStorage(asTypedEvmAddress(id), uint256Key);

        final var expectedVal = UInt256.fromBytes(Bytes.wrap(contractVal.getValue()));
        assertEquals(expectedVal, uint256Val);
    }

    @Test
    void getForUnknownReturnsZero() {
        final var unit256Val = subject.getStorage(asTypedEvmAddress(id), UInt256.MAX_VALUE);

        assertEquals(UInt256.ZERO, unit256Val);
    }

    @Test
    void fetchWithValueWorks() {
        given(blobs.get(blobKey)).willReturn(blobVal);

        final var blobBytes = subject.fetchCodeIfPresent(asTypedEvmAddress(id));

        final var expectedVal = Bytes.of(blobVal.getData());
        assertEquals(expectedVal, blobBytes);
    }

    @Test
    void fetchWithoutValueReturnsNull() {
        assertNull(subject.fetchCodeIfPresent(asTypedEvmAddress(id)));
    }

    @Test
    void failsWithInvalidTokenIdGivenMissing() {
        assertFailsWith(() -> subject.typeOf(tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.nameOf(tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.supplyOf(tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.symbolOf(tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.decimalsOf(tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.balanceOf(accountId, tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.isKyc(accountId, tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.defaultFreezeStatus(tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.defaultKycStatus(tokenId), INVALID_TOKEN_ID);
        assertFailsWith(() -> subject.isFrozen(accountId, tokenId), INVALID_TOKEN_ID);
    }

    @Test
    void getsExpectedTokenMeta() {
        given(tokens.get(tokenNum)).willReturn(token);

        assertEquals(name, subject.nameOf(tokenId));
        assertEquals(symbol, subject.symbolOf(tokenId));
        assertEquals(decimals, subject.decimalsOf(tokenId));
        assertEquals(totalSupply, subject.supplyOf(tokenId));
        assertEquals(type, subject.typeOf(tokenId));
        assertEquals(accountsFrozenByDefault, subject.defaultFreezeStatus(tokenId));
        assertEquals(accountsKycGrantedByDefault, subject.defaultKycStatus(tokenId));
    }

    @Test
    void rejectsMissingAccount() {
        given(tokens.get(tokenNum)).willReturn(token);
        assertFailsWith(() -> subject.isKyc(accountId, tokenId), INVALID_ACCOUNT_ID);
        assertFailsWith(() -> subject.balanceOf(accountId, tokenId), INVALID_ACCOUNT_ID);
        assertFailsWith(() -> subject.isFrozen(accountId, tokenId), INVALID_ACCOUNT_ID);
    }

    @Test
    void getsZeroBalanceIfNoAssociation() {
        given(tokens.get(tokenNum)).willReturn(token);
        given(accounts.containsKey(accountNum)).willReturn(true);
        assertEquals(0, subject.balanceOf(accountId, tokenId));
    }

    @Test
    void getKeys() throws DecoderException {
        token.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey());
        token.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asJKey());
        token.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey());
        token.setWipeKey(TxnHandlingScenario.TOKEN_WIPE_KT.asJKey());
        token.setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKey());
        token.setFeeScheduleKey(TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT.asJKey());
        token.setPauseKey(TxnHandlingScenario.TOKEN_PAUSE_KT.asJKey());
        given(tokens.get(tokenNum)).willReturn(token);

        assertEquals(
                TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey(),
                subject.keyOf(tokenId, TokenProperty.ADMIN_KEY));
        assertEquals(
                TxnHandlingScenario.TOKEN_KYC_KT.asJKey(),
                subject.keyOf(tokenId, TokenProperty.KYC_KEY));
        assertEquals(
                TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey(),
                subject.keyOf(tokenId, TokenProperty.FREEZE_KEY));
        assertEquals(
                TxnHandlingScenario.TOKEN_WIPE_KT.asJKey(),
                subject.keyOf(tokenId, TokenProperty.WIPE_KEY));
        assertEquals(
                TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKey(),
                subject.keyOf(tokenId, TokenProperty.SUPPLY_KEY));
        assertEquals(
                TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT.asJKey(),
                subject.keyOf(tokenId, TokenProperty.FEE_SCHEDULE_KEY));
        assertEquals(
                TxnHandlingScenario.TOKEN_PAUSE_KT.asJKey(),
                subject.keyOf(tokenId, TokenProperty.PAUSE_KEY));
    }

    @Test
    void getInvalidTypeOfKeyFails() {
        given(tokens.get(tokenNum)).willReturn(token);

        assertThrows(
                InvalidTransactionException.class,
                () -> subject.keyOf(tokenId, TokenProperty.TOKEN_TYPE));
    }

    @Test
    void getsExpectedIsKycTokenStatusIfAssociationExists() {
        given(tokens.get(tokenNum)).willReturn(token);
        given(accounts.containsKey(accountNum)).willReturn(true);
        final var relStatus = new MerkleTokenRelStatus(balance, false, true, false);
        given(tokenAssociations.get(EntityNumPair.fromAccountTokenRel(accountId, tokenId)))
                .willReturn(relStatus);
        assertTrue(subject.isKyc(accountId, tokenId));
    }

    @Test
    void getsExpectedBalanceIfAssociationExists() {
        given(tokens.get(tokenNum)).willReturn(token);
        given(accounts.containsKey(accountNum)).willReturn(true);
        final var relStatus = new MerkleTokenRelStatus(balance, false, false, false);
        given(tokenAssociations.get(EntityNumPair.fromAccountTokenRel(accountId, tokenId)))
                .willReturn(relStatus);
        assertEquals(balance, subject.balanceOf(accountId, tokenId));
    }

    @Test
    void getsExpectedIsFrozenTokenStatusIfAssociationExists() {
        given(tokens.get(tokenNum)).willReturn(token);
        given(accounts.containsKey(accountNum)).willReturn(true);
        final var relStatus = new MerkleTokenRelStatus(balance, true, false, false);
        given(tokenAssociations.get(EntityNumPair.fromAccountTokenRel(accountId, tokenId)))
                .willReturn(relStatus);
        assertTrue(subject.isFrozen(accountId, tokenId));
    }

    @Test
    void failsIfNftIdNotPresent() {
        assertFailsRevertingWith(
                () -> subject.approvedSpenderOf(nft), INVALID_TOKEN_NFT_SERIAL_NUMBER);
    }

    @Test
    void returnsApprovedAddressIfPresent() {
        given(nfts.get(nftKey)).willReturn(withApprovedSpender);
        final var expected = withApprovedSpender.getSpender().toEvmAddress();
        final var actual = subject.approvedSpenderOf(nft);
        assertEquals(expected, actual);
    }

    @Test
    void returnsFalseIfOwnerDoesntExist() {
        assertFalse(subject.isOperator(accountId, spenderId, tokenId));
    }

    @Test
    void returnsFalseIfOperatorDoesntExist() {
        final var owner = new MerkleAccount();
        given(accounts.get(accountNum)).willReturn(owner);
        assertFalse(subject.isOperator(accountId, spenderId, tokenId));
    }

    @Test
    void returnsFalseIfOperatorNotApproved() {
        final var owner = new MerkleAccount();
        final var operator = new MerkleAccount();
        given(accounts.get(accountNum)).willReturn(owner);
        given(accounts.get(spenderNum)).willReturn(operator);
        owner.setApproveForAllNfts(Set.of(new FcTokenAllowanceId(tokenNum, treasuryNum)));
        assertFalse(subject.isOperator(accountId, spenderId, tokenId));
    }

    @Test
    void returnsTrueIfOperatorApproved() {
        final var owner = new MerkleAccount();
        final var operator = new MerkleAccount();
        given(accounts.get(accountNum)).willReturn(owner);
        given(accounts.get(spenderNum)).willReturn(operator);
        owner.setApproveForAllNfts(Set.of(new FcTokenAllowanceId(tokenNum, spenderNum)));
        assertTrue(subject.isOperator(accountId, spenderId, tokenId));
    }

    @Test
    void ownerOfThrowsForMissingNft() {
        assertFailsWith(() -> subject.ownerOf(nft), INVALID_TOKEN_NFT_SERIAL_NUMBER);
    }

    @Test
    void ownerOfTranslatesWildcardOwner() {
        given(nfts.get(nftKey)).willReturn(treasuryOwned);
        treasuryOwned.setKey(nftKey);
        given(tokens.get(nftKey.getHiOrderAsNum())).willReturn(token);
        final var actual = subject.ownerOf(nft);
        assertEquals(treasuryAddress, actual);
    }

    @Test
    void ownerOfReturnsNonTreasuryOwner() {
        given(nfts.get(nftKey)).willReturn(accountOwned);
        final var expected = accountNum.toEvmAddress();
        final var actual = subject.ownerOf(nft);
        assertEquals(expected, actual);
    }

    @Test
    void allowanceOfThrowsRevertingOnMissingOwner() {
        assertFailsRevertingWith(
                () -> subject.allowanceOf(accountId, spenderId, tokenId),
                INVALID_ALLOWANCE_OWNER_ID);
    }

    @Test
    void allowanceOfReturnsZeroForNoAllowance() {
        final var owner = new MerkleAccount();
        given(accounts.get(accountNum)).willReturn(owner);
        assertEquals(0L, subject.allowanceOf(accountId, spenderId, tokenId));
    }

    @Test
    void allowanceOfReturnsKnownAllowanceIfPresent() {
        final var fcTokenAllowanceId = FcTokenAllowanceId.from(tokenId, spenderId);
        final var owner = new MerkleAccount();
        final SortedMap<FcTokenAllowanceId, Long> allowances = new TreeMap<>();
        allowances.put(fcTokenAllowanceId, 123L);
        owner.setFungibleTokenAllowances(allowances);
        given(accounts.get(accountNum)).willReturn(owner);
        assertEquals(123L, subject.allowanceOf(accountId, spenderId, tokenId));
    }

    private static final NftId nft = new NftId(0, 0, 123, 456);
    private static final EntityNumPair nftKey = EntityNumPair.fromNftId(nft);

    private static final MerkleUniqueToken treasuryOwned =
            new MerkleUniqueToken(
                    MISSING_ENTITY_ID,
                    "There, the eyes are".getBytes(StandardCharsets.UTF_8),
                    new RichInstant(1, 2));
    private static final int decimals = 666666;
    private static final long someExpiry = 1_234_567L;
    private static final long totalSupply = 4242;
    private static final long balance = 2424;
    private static final TokenType type = TokenType.NON_FUNGIBLE_UNIQUE;
    private static final String name = "Sunlight on a broken column";
    private static final String symbol = "THM1925";
    private static final EntityNum tokenNum = EntityNum.fromLong(666);
    private static final EntityNum accountNum = EntityNum.fromLong(888);
    private static final EntityNum treasuryNum = EntityNum.fromLong(999);
    private static final EntityNum spenderNum = EntityNum.fromLong(111);
    private static final boolean isKyc = false;
    private static final boolean accountsFrozenByDefault = false;
    private static final boolean accountsKycGrantedByDefault = true;
    private static final MerkleUniqueToken accountOwned =
            new MerkleUniqueToken(
                    accountNum.toEntityId(),
                    "There, is a tree swinging".getBytes(StandardCharsets.UTF_8),
                    new RichInstant(2, 3));

    private static final MerkleUniqueToken withApprovedSpender =
            new MerkleUniqueToken(
                    accountNum.toEntityId(),
                    "And voices are".getBytes(StandardCharsets.UTF_8),
                    new RichInstant(1, 2));

    static {
        withApprovedSpender.setSpender(spenderNum.toEntityId());
    }

    private static final Address treasuryAddress = treasuryNum.toEvmAddress();
    private static final TokenID tokenId = tokenNum.toGrpcTokenId();
    private static final AccountID accountId = accountNum.toGrpcAccountId();
    private static final AccountID spenderId = spenderNum.toGrpcAccountId();
    private static final MerkleToken token =
            new MerkleToken(
                    someExpiry,
                    totalSupply,
                    decimals,
                    symbol,
                    name,
                    false,
                    true,
                    treasuryNum.toEntityId());

    {
        token.setTokenType(type);
    }
}
