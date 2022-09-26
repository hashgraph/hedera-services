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

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.ContractCustomizer;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaStackedWorldStateUpdaterTest {
    private static final Address alias =
            Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final Address alias2 =
            Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbc");
    private static final Address sponsor = Address.fromHexString("0xcba");
    private static final Address address = Address.fromHexString("0xabc");
    private static final Address otherAddress = Address.fromHexString("0xdef");
    private static final ContractID addressId = EntityIdUtils.contractIdFromEvmAddress(address);

    @Mock private ContractAliases aliases;
    @Mock private WorldLedgers trackingLedgers;

    @Mock(extraInterfaces = {HederaWorldUpdater.class})
    private AbstractLedgerWorldUpdater<HederaMutableWorldState, Account> updater;

    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    @Mock private HederaMutableWorldState worldState;
    @Mock private HederaStackedWorldStateUpdater.CustomizerFactory customizerFactory;
    @Mock private ContractCustomizer customizer;
    @Mock private HederaAccountCustomizer accountCustomizer;
    @Mock private GlobalDynamicProperties globalDynamicProperties;

    private HederaStackedWorldStateUpdater subject;

    @BeforeEach
    void setUp() {
        subject =
                new HederaStackedWorldStateUpdater(
                        updater, worldState, trackingLedgers, globalDynamicProperties);
    }

    @Test
    void namedelegatesTokenAccountTest() {
        final var someAddress = Address.BLS12_MAP_FP2_TO_G2;
        given(trackingLedgers.isTokenAddress(someAddress)).willReturn(true);
        assertTrue(subject.isTokenAddress(someAddress));
    }

    @Test
    void recognizesTreasuryAccount() {
        final var treasuryAddress = Address.BLS12_MAP_FP2_TO_G2;
        final var treasuryAddressId = EntityIdUtils.accountIdFromEvmAddress(treasuryAddress);
        given(aliases.resolveForEvm(treasuryAddress)).willReturn(treasuryAddress);
        given(trackingLedgers.accounts()).willReturn(accountsLedger);
        given(trackingLedgers.aliases()).willReturn(aliases);
        given(accountsLedger.get(treasuryAddressId, NUM_TREASURY_TITLES)).willReturn(1);
        assertTrue(subject.contractIsTokenTreasury(treasuryAddress));
        given(accountsLedger.get(treasuryAddressId, NUM_TREASURY_TITLES)).willReturn(0);
        assertFalse(subject.contractIsTokenTreasury(treasuryAddress));
    }

    @Test
    void recognizesNonZeroTokenBalanceAccount() {
        final var treasuryAddress = Address.BLS12_MAP_FP2_TO_G2;
        final var positiveBalanceId = EntityIdUtils.accountIdFromEvmAddress(treasuryAddress);
        given(aliases.resolveForEvm(treasuryAddress)).willReturn(treasuryAddress);
        given(trackingLedgers.accounts()).willReturn(accountsLedger);
        given(trackingLedgers.aliases()).willReturn(aliases);
        given(accountsLedger.get(positiveBalanceId, NUM_POSITIVE_BALANCES)).willReturn(1);
        assertTrue(subject.contractHasAnyBalance(treasuryAddress));
        given(accountsLedger.get(positiveBalanceId, NUM_POSITIVE_BALANCES)).willReturn(0);
        assertFalse(subject.contractHasAnyBalance(treasuryAddress));
    }

    @Test
    void recognizesAccountWhoStillOwnsNfts() {
        final var treasuryAddress = Address.BLS12_MAP_FP2_TO_G2;
        final var positiveBalanceId = EntityIdUtils.accountIdFromEvmAddress(treasuryAddress);
        given(aliases.resolveForEvm(treasuryAddress)).willReturn(treasuryAddress);
        given(trackingLedgers.accounts()).willReturn(accountsLedger);
        given(trackingLedgers.aliases()).willReturn(aliases);
        given(accountsLedger.get(positiveBalanceId, NUM_NFTS_OWNED)).willReturn(1L);
        assertTrue(subject.contractOwnsNfts(treasuryAddress));
        given(accountsLedger.get(positiveBalanceId, NUM_NFTS_OWNED)).willReturn(0L);
        assertFalse(subject.contractOwnsNfts(treasuryAddress));
    }

    @Test
    void understandsRedirectsIfDisabled() {
        assertFalse(subject.isTokenRedirect(Address.ALTBN128_PAIRING));
    }

    @Test
    void understandsRedirectsIfMissingTokens() {
        given(globalDynamicProperties.isRedirectTokenCallsEnabled()).willReturn(true);
        given(trackingLedgers.isTokenAddress(Address.ALTBN128_PAIRING)).willReturn(true);
        assertTrue(subject.isTokenRedirect(Address.ALTBN128_PAIRING));
    }

    @Test
    void usesAliasesForDecodingHelp() {
        given(aliases.resolveForEvm(alias)).willReturn(sponsor);
        given(trackingLedgers.aliases()).willReturn(aliases);
        given(trackingLedgers.canonicalAddress(alias)).willReturn(alias);

        final var resolved = subject.unaliased(alias.toArrayUnsafe());
        assertArrayEquals(sponsor.toArrayUnsafe(), resolved);
    }

    @Test
    void usesAliasesForPermissiveDecodingHelp() {
        given(aliases.resolveForEvm(alias)).willReturn(sponsor);
        given(trackingLedgers.aliases()).willReturn(aliases);

        final var resolved = subject.permissivelyUnaliased(alias.toArrayUnsafe());
        assertArrayEquals(sponsor.toArrayUnsafe(), resolved);
    }

    @Test
    void unaliasingFailsWhenNotUsingCanonicalAddress() {
        given(trackingLedgers.canonicalAddress(alias)).willReturn(alias2);

        assertArrayEquals(new byte[20], subject.unaliased(alias.toArrayUnsafe()));
    }

    @Test
    void detectsMutableLedgers() {
        given(trackingLedgers.areMutable()).willReturn(true);

        assertTrue(subject.isInTransaction());
    }

    @Test
    void linksAliasWhenReservingNewContractId() {
        withMockCustomizerFactory(
                () -> {
                    given(worldState.newContractAddress(sponsor)).willReturn(address);
                    given(trackingLedgers.aliases()).willReturn(aliases);
                    given(trackingLedgers.accounts()).willReturn(accountsLedger);
                    given(aliases.resolveForEvm(sponsor)).willReturn(sponsor);
                    given(customizerFactory.apply(any(), any())).willReturn(customizer);
                    given(customizer.accountCustomizer()).willReturn(accountCustomizer);

                    final var created = subject.newAliasedContractAddress(sponsor, alias);

                    assertSame(address, created);
                    assertEquals(addressId, subject.idOfLastNewAddress());
                    verify(aliases).link(alias, address);
                    verify(accountCustomizer).maxAutomaticAssociations(0);
                });
    }

    @Test
    void usesCanonicalAddressFromTrackingLedgers() {
        given(trackingLedgers.canonicalAddress(sponsor)).willReturn(alias);

        assertSame(alias, subject.priorityAddress(sponsor));
    }

    @Test
    void doesntRelinkAliasIfActiveAndExtant() {
        withMockCustomizerFactory(
                () -> {
                    final var targetId = EntityIdUtils.accountIdFromEvmAddress(otherAddress);
                    given(worldState.newContractAddress(sponsor)).willReturn(address);
                    given(trackingLedgers.accounts()).willReturn(accountsLedger);
                    given(trackingLedgers.aliases()).willReturn(aliases);
                    given(aliases.isInUse(alias)).willReturn(true);
                    given(aliases.resolveForEvm(sponsor)).willReturn(sponsor);
                    given(aliases.resolveForEvm(alias)).willReturn(otherAddress);
                    given(accountsLedger.exists(targetId)).willReturn(true);
                    given(globalDynamicProperties.areContractAutoAssociationsEnabled())
                            .willReturn(true);

                    final var created = subject.newAliasedContractAddress(sponsor, alias);

                    assertSame(address, created);
                    assertEquals(addressId, subject.idOfLastNewAddress());
                    verify(aliases, never()).link(alias, address);
                });
    }

    @Test
    void doesRelinkAliasIfActiveButWithMissingTarget() {
        withMockCustomizerFactory(
                () -> {
                    given(worldState.newContractAddress(sponsor)).willReturn(address);
                    given(trackingLedgers.accounts()).willReturn(accountsLedger);
                    given(trackingLedgers.aliases()).willReturn(aliases);
                    given(aliases.isInUse(alias)).willReturn(true);
                    given(aliases.resolveForEvm(sponsor)).willReturn(sponsor);
                    given(aliases.resolveForEvm(alias)).willReturn(otherAddress);
                    given(globalDynamicProperties.areContractAutoAssociationsEnabled())
                            .willReturn(true);

                    final var created = subject.newAliasedContractAddress(sponsor, alias);

                    assertSame(address, created);
                    assertEquals(addressId, subject.idOfLastNewAddress());
                    verify(aliases).link(alias, address);
                });
    }

    @Test
    void allocatesNewContractAddress() {
        withMockCustomizerFactory(
                () -> {
                    final var sponsoredId = ContractID.newBuilder().setContractNum(2).build();
                    final var sponsorAddr =
                            Address.wrap(
                                    Bytes.wrap(
                                            EntityIdUtils.asEvmAddress(
                                                    ContractID.newBuilder()
                                                            .setContractNum(1)
                                                            .build())));
                    given(trackingLedgers.aliases()).willReturn(aliases);
                    given(aliases.resolveForEvm(sponsorAddr)).willReturn(sponsorAddr);
                    given(globalDynamicProperties.areContractAutoAssociationsEnabled())
                            .willReturn(true);

                    final var sponsoredAddr =
                            Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(sponsoredId)));
                    given(worldState.newContractAddress(sponsorAddr)).willReturn(sponsoredAddr);
                    final var allocated = subject.newContractAddress(sponsorAddr);
                    final var sponsorAid =
                            EntityIdUtils.accountIdFromEvmAddress(sponsorAddr.toArrayUnsafe());
                    final var allocatedAid =
                            EntityIdUtils.accountIdFromEvmAddress(allocated.toArrayUnsafe());

                    assertEquals(sponsorAid.getRealmNum(), allocatedAid.getRealmNum());
                    assertEquals(sponsorAid.getShardNum(), allocatedAid.getShardNum());
                    assertEquals(sponsorAid.getAccountNum() + 1, allocatedAid.getAccountNum());
                    assertEquals(sponsoredId, subject.idOfLastNewAddress());
                });
    }

    @Test
    void returnsParentCustomizationIfNoFrameCreationPending() {
        given(updater.customizerForPendingCreation()).willReturn(customizer);

        assertSame(customizer, subject.customizerForPendingCreation());
    }

    @Test
    void returnsCustomizationIfFrameCreationPending() {
        given(updater.customizerForPendingCreation()).willReturn(customizer);

        assertSame(customizer, subject.customizerForPendingCreation());
    }

    @Test
    void canSponsorWithAlias() {
        withMockCustomizerFactory(
                () -> {
                    final var sponsoredId = ContractID.newBuilder().setContractNum(2).build();
                    final var sponsorAddr =
                            Address.wrap(
                                    Bytes.wrap(
                                            EntityIdUtils.asEvmAddress(
                                                    ContractID.newBuilder()
                                                            .setContractNum(1)
                                                            .build())));
                    final var sponsorAid =
                            EntityIdUtils.accountIdFromEvmAddress(sponsorAddr.toArrayUnsafe());

                    given(aliases.resolveForEvm(alias)).willReturn(sponsorAddr);
                    given(trackingLedgers.aliases()).willReturn(aliases);
                    given(trackingLedgers.accounts()).willReturn(accountsLedger);
                    given(globalDynamicProperties.areContractAutoAssociationsEnabled())
                            .willReturn(true);

                    final var sponsoredAddr =
                            Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(sponsoredId)));
                    given(worldState.newContractAddress(sponsorAddr)).willReturn(sponsoredAddr);
                    given(customizerFactory.apply(sponsorAid, accountsLedger))
                            .willReturn(customizer);

                    final var allocated = subject.newContractAddress(alias);
                    final var allocatedAid =
                            EntityIdUtils.accountIdFromEvmAddress(allocated.toArrayUnsafe());

                    assertEquals(sponsorAid.getRealmNum(), allocatedAid.getRealmNum());
                    assertEquals(sponsorAid.getShardNum(), allocatedAid.getShardNum());
                    assertEquals(sponsorAid.getAccountNum() + 1, allocatedAid.getAccountNum());
                    assertEquals(sponsoredId, subject.idOfLastNewAddress());
                    assertSame(customizer, subject.customizerForPendingCreation());
                });
    }

    @Test
    void revertBehavesAsExpected() {
        subject.countIdsAllocatedByStacked(3);
        subject.addSbhRefund(123L);
        assertEquals(123L, subject.getSbhRefund());
        subject.revert();
        assertEquals(0L, subject.getSbhRefund());
        verify(worldState, times(3)).reclaimContractId();
    }

    @Test
    void updaterReturnsStacked() {
        var updater = subject.updater();
        assertEquals(HederaStackedWorldStateUpdater.class, updater.getClass());
    }

    private void withMockCustomizerFactory(final Runnable spec) {
        HederaStackedWorldStateUpdater.setCustomizerFactory(customizerFactory);
        spec.run();
        HederaStackedWorldStateUpdater.setCustomizerFactory(
                ContractCustomizer::fromSponsorContract);
    }
}
