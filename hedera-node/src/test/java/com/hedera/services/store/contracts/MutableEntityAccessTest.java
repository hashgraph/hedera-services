/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.virtualmap.VirtualMap;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MutableEntityAccessTest {
    @Mock private HederaLedger ledger;
    @Mock private Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> supplierBytecode;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecodeStorage;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRelsLedger;

    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor accessor;
    @Mock private SizeLimitedStorage storage;
    @Mock private AliasManager aliasManager;

    private MutableEntityAccess subject;

    private final AccountID id = IdUtils.asAccount("0.0.1234");
    private final long balance = 1234L;

    private final UInt256 contractStorageKey = UInt256.ONE;
    private final UInt256 contractStorageValue = UInt256.MAX_VALUE;

    private final Bytes bytecode = Bytes.of("contract-code".getBytes());
    private final VirtualBlobKey expectedBytecodeKey =
            new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, (int) id.getAccountNum());
    private final VirtualBlobValue expectedBytecodeValue = new VirtualBlobValue(bytecode.toArray());

    @BeforeEach
    void setUp() {
        given(ledger.getTokenRelsLedger()).willReturn(tokenRelsLedger);
        given(ledger.getAccountsLedger()).willReturn(accountsLedger);
        given(ledger.getNftsLedger()).willReturn(nftsLedger);

        subject =
                new MutableEntityAccess(
                        ledger, aliasManager, txnCtx, storage, tokensLedger, supplierBytecode);
    }

    @Test
    void recordsViaSizeLimitedStorage() {
        subject.recordNewKvUsageTo(accountsLedger);

        verify(storage).recordNewKvUsageTo(accountsLedger);
    }

    @Test
    void flushesAsExpected() {
        subject.flushStorage(accountsLedger);

        verify(storage).validateAndCommit(accountsLedger);
    }

    @Test
    void setsSelfInLedger() {
        verify(ledger).setMutableEntityAccess(subject);
    }

    @Test
    void returnsTokensLedgerChangeSetForManagedChanges() {
        final var mockChanges = "N?A";
        given(tokensLedger.changeSetSoFar()).willReturn(mockChanges);
        assertEquals(mockChanges, subject.currentManagedChangeSet());
    }

    @Test
    void beginsLedgerTxnIfContractCreateIsActive() {
        givenActive(ContractCreate);
        subject.startAccess();
        verify(storage).beginSession();
    }

    @Test
    void doesntBeginLedgerTxnIfNonContractOpIsActive() {
        givenActive(HederaFunctionality.TokenMint);
        subject.startAccess();
        verify(tokensLedger, never()).begin();
    }

    @Test
    void delegatesLedgerAccess() {
        final var worldLedgers = subject.worldLedgers();

        assertSame(tokenRelsLedger, worldLedgers.tokenRels());
        assertSame(accountsLedger, worldLedgers.accounts());
        assertSame(nftsLedger, worldLedgers.nfts());
    }

    @Test
    void customizesAccount() {
        // when:
        subject.customize(id, new HederaAccountCustomizer());

        // then:
        verify(ledger).customizePotentiallyDeleted(eq(id), any());
    }

    @Test
    void delegatesAlias() {
        final var pretend = ByteString.copyFromUtf8("YAWN");
        given(ledger.alias(id)).willReturn(pretend);
        assertSame(pretend, subject.alias(id));
    }

    @Test
    void getsBalance() {
        // given:
        given(ledger.getBalance(id)).willReturn(balance);

        // when:
        final var result = subject.getBalance(id);

        // then:
        assertEquals(balance, result);
        // and:
        verify(ledger).getBalance(id);
    }

    @Test
    void checksIfUsableOk() {
        given(ledger.usabilityOf(id)).willReturn(OK);

        assertTrue(subject.isUsable(id));
    }

    @Test
    void checksIfUsableNotOk() {
        given(ledger.usabilityOf(id)).willReturn(ACCOUNT_DELETED);

        assertFalse(subject.isUsable(id));
    }

    @Test
    void checksIfExtant() {
        // given:
        given(ledger.exists(id)).willReturn(true);

        // when:
        assertTrue(subject.isExtant(id));

        // and:
        verify(ledger).exists(id);
    }

    @Test
    void checksIfTokenAccount() {
        // given:
        given(tokensLedger.exists(EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr)))
                .willReturn(true);

        // when:
        assertTrue(subject.isTokenAccount(fungibleTokenAddr));

        // and:
        verify(tokensLedger).exists(EntityIdUtils.tokenIdFromEvmAddress(fungibleTokenAddr));
    }

    @Test
    void putsNonZeroContractStorageValue() {
        subject.putStorage(id, contractStorageKey, contractStorageValue);

        verify(storage).putStorage(id, contractStorageKey, contractStorageValue);
    }

    @Test
    void getsExpectedContractStorageValue() {
        // and:
        given(storage.getStorage(id, contractStorageKey)).willReturn(UInt256.MAX_VALUE);

        // when:
        final var result = subject.getStorage(id, contractStorageKey);

        // then:
        assertEquals(UInt256.MAX_VALUE, result);
    }

    @Test
    void storesBlob() {
        // given:
        given(supplierBytecode.get()).willReturn(bytecodeStorage);

        // when:
        subject.storeCode(id, bytecode);

        // then:
        verify(bytecodeStorage).put(expectedBytecodeKey, expectedBytecodeValue);
    }

    @Test
    void fetchesEmptyBytecode() {
        given(supplierBytecode.get()).willReturn(bytecodeStorage);

        assertNull(subject.fetchCodeIfPresent(id));
    }

    @Test
    void fetchesBytecode() {
        given(supplierBytecode.get()).willReturn(bytecodeStorage);
        given(bytecodeStorage.get(expectedBytecodeKey)).willReturn(expectedBytecodeValue);

        final var result = subject.fetchCodeIfPresent(id);

        assertEquals(bytecode, result);
        verify(bytecodeStorage).get(expectedBytecodeKey);
    }

    private void givenActive(final HederaFunctionality function) {
        given(accessor.getFunction()).willReturn(function);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
