/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static com.hedera.services.exceptions.InsufficientFundsException.messageFor;
import static com.hedera.services.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.services.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HederaLedgerTest extends BaseHederaLedgerTestHelper {
    @Mock private AutoCreationLogic autoCreationLogic;

    @BeforeEach
    private void setup() {
        commonSetup();
        setupWithMockLedger();
    }

    @Test
    void understandsTreasuryStatus() {
        given(accountsLedger.get(misc, NUM_TREASURY_TITLES)).willReturn(0);
        assertFalse(subject.isKnownTreasury(misc));
        given(accountsLedger.get(misc, NUM_TREASURY_TITLES)).willReturn(1);
        assertTrue(subject.isKnownTreasury(misc));
    }

    @Test
    void understandsNonZeroBalanceValidation() {
        given(accountsLedger.get(misc, NUM_POSITIVE_BALANCES)).willReturn(0);
        assertFalse(subject.hasAnyFungibleTokenBalance(misc));
        given(accountsLedger.get(misc, NUM_POSITIVE_BALANCES)).willReturn(1);
        assertTrue(subject.hasAnyFungibleTokenBalance(misc));
        given(accountsLedger.get(misc, NUM_NFTS_OWNED)).willReturn(0L);
        assertFalse(subject.hasAnyNfts(misc));
        given(accountsLedger.get(misc, NUM_NFTS_OWNED)).willReturn(1L);
        assertTrue(subject.hasAnyNfts(misc));
    }

    @Test
    void usabilityDetectsInvalidId() {
        given(accountsLedger.get(misc, IS_DELETED)).willThrow(MissingEntityException.class);
        final var actual = subject.usabilityOf(misc);
        assertEquals(INVALID_ACCOUNT_ID, actual);
    }

    @Test
    void usabilityDetectsDeletedAccount() {
        given(accountsLedger.get(misc, IS_DELETED)).willReturn(true);
        final var actual = subject.usabilityOf(misc);
        assertEquals(ACCOUNT_DELETED, actual);
    }

    @Test
    void usabilityDetectsDeletedContract() {
        given(accountsLedger.get(misc, IS_DELETED)).willReturn(true);
        given(accountsLedger.get(misc, IS_SMART_CONTRACT)).willReturn(true);
        final var actual = subject.usabilityOf(misc);
        assertEquals(CONTRACT_DELETED, actual);
    }

    @Test
    void usabilityDelegatesExtantNonDeletedToValidator() {
        final var actual = subject.usabilityOf(misc);
        assertEquals(OK, actual);
    }

    @Test
    void canIncrementTreasuryTitles() {
        given(accountsLedger.get(misc, NUM_TREASURY_TITLES)).willReturn(1);

        subject.incrementNumTreasuryTitles(misc);

        verify(accountsLedger).set(misc, NUM_TREASURY_TITLES, 2);
    }

    @Test
    void canDecrementTreasuryTitles() {
        given(accountsLedger.get(misc, NUM_TREASURY_TITLES)).willReturn(1);

        subject.decrementNumTreasuryTitles(misc);

        verify(accountsLedger).set(misc, NUM_TREASURY_TITLES, 0);
    }

    @Test
    void ledgerGettersWork() {
        assertSame(nftsLedger, subject.getNftsLedger());
        assertSame(accountsLedger, subject.getAccountsLedger());
        assertSame(tokenRelsLedger, subject.getTokenRelsLedger());
    }

    @Test
    void zeroTouchesNetTransfers() {
        final var net = subject.netTokenTransfersInTxn();
        assertEquals(0, net.size());
    }

    @Test
    void indicatesNoChangeSetIfNotInTx() {
        final var summary = subject.currentChangeSet();

        verify(accountsLedger, never()).changeSetSoFar();
        assertEquals(HederaLedger.NO_ACTIVE_TXN_CHANGE_SET, summary);
    }

    @Test
    void delegatesChangeSetIfInTxn() {
        final var zeroingGenesis = "{0.0.2: [BALANCE -> 0]}";
        final var creatingTreasury = "{0.0.2 <-> 0.0.1001: [TOKEN_BALANCE -> 1_000_000]}";
        final var changingOwner =
                "{NftId{shard=0, realm=0, num=10000, serialNo=1234}: "
                        + "[OWNER -> EntityId{shard=3, realm=4, num=5}]}";
        given(accountsLedger.isInTransaction()).willReturn(true);
        given(accountsLedger.changeSetSoFar()).willReturn(zeroingGenesis);
        given(tokenRelsLedger.changeSetSoFar()).willReturn(creatingTreasury);
        given(nftsLedger.changeSetSoFar()).willReturn(changingOwner);
        given(mutableEntityAccess.currentManagedChangeSet()).willReturn("NONSENSE");

        final var summary = subject.currentChangeSet();

        verify(accountsLedger).changeSetSoFar();
        final var desired =
                "--- ACCOUNTS ---\n"
                        + "{0.0.2: [BALANCE -> 0]}\n"
                        + "--- TOKEN RELATIONSHIPS ---\n"
                        + "{0.0.2 <-> 0.0.1001: [TOKEN_BALANCE -> 1_000_000]}\n"
                        + "--- NFTS ---\n"
                        + "{NftId{shard=0, realm=0, num=10000, serialNo=1234}: [OWNER ->"
                        + " EntityId{shard=3, realm=4, num=5}]}\n"
                        + "--- TOKENS ---\n"
                        + "NONSENSE";
        assertEquals(desired, summary);
    }

    @Test
    void delegatesExists() {
        final var missing = asAccount("55.66.77");

        final var hasMissing = subject.exists(missing);
        final var hasGenesis = subject.exists(genesis);

        verify(accountsLedger, times(2)).exists(any());
        assertTrue(hasGenesis);
        assertFalse(hasMissing);
    }

    @Test
    void setsCreatorOnHistorian() {
        verify(historian).setCreator(creator);
    }

    @Test
    void delegatesToCorrectContractProperty() {
        subject.isSmartContract(genesis);

        verify(accountsLedger).get(genesis, IS_SMART_CONTRACT);
    }

    @Test
    void delegatesToCorrectDeletionProperty() {
        subject.isDeleted(genesis);

        verify(accountsLedger).get(genesis, IS_DELETED);
    }

    @Test
    void delegatesToCorrectSigReqProperty() {
        subject.isReceiverSigRequired(genesis);

        verify(accountsLedger).get(genesis, IS_RECEIVER_SIG_REQUIRED);
    }

    @Test
    void recognizesDetachedAccount() {
        validator = mock(OptionValidator.class);
        given(validator.isAfterConsensusSecond(anyLong())).willReturn(false);
        given(accountsLedger.get(genesis, BALANCE)).willReturn(0L);
        subject =
                new HederaLedger(
                        tokenStore,
                        ids,
                        creator,
                        validator,
                        new SideEffectsTracker(),
                        historian,
                        tokensLedger,
                        accountsLedger,
                        transferLogic,
                        autoCreationLogic);

        assertTrue(subject.isDetached(genesis));
    }

    @Test
    void recognizesDetachedContract() {
        validator = mock(OptionValidator.class);
        given(validator.isAfterConsensusSecond(anyLong())).willReturn(false);
        given(accountsLedger.get(genesis, BALANCE)).willReturn(0L);
        given(accountsLedger.get(genesis, IS_SMART_CONTRACT)).willReturn(true);
        subject =
                new HederaLedger(
                        tokenStore,
                        ids,
                        creator,
                        validator,
                        new SideEffectsTracker(),
                        historian,
                        tokensLedger,
                        accountsLedger,
                        transferLogic,
                        autoCreationLogic);

        assertTrue(subject.isDetached(genesis));
    }

    @Test
    void recognizesCannotBeDetachedIfValidatorIsOk() {
        validator = mock(OptionValidator.class);
        given(validator.expiryStatusGiven(any(), any())).willReturn(OK);
        assertFalse(subject.isDetached(genesis));
    }

    @Test
    void recognizesDetachedIfValidatorIsNotOk() {
        validator = mock(OptionValidator.class);
        given(validator.expiryStatusGiven(any(), any()))
                .willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        assertFalse(subject.isDetached(genesis));
    }

    @Test
    void delegatesToCorrectExpiryProperty() {
        subject.expiry(genesis);

        verify(accountsLedger).get(genesis, EXPIRY);
    }

    @Test
    void delegatesToCorrectAutoRenewProperty() {
        subject.autoRenewPeriod(genesis);

        verify(accountsLedger).get(genesis, AUTO_RENEW_PERIOD);
    }

    @Test
    void delegatesToCorrectMemoProperty() {
        subject.memo(genesis);

        verify(accountsLedger).get(genesis, MEMO);
    }

    @Test
    void delegatesToCorrectAliasProperty() {
        subject.alias(genesis);

        verify(accountsLedger).get(genesis, ALIAS);
    }

    @Test
    void delegatesToClearAliasProperly() {
        subject.clearAlias(genesis);

        verify(accountsLedger).set(genesis, ALIAS, ByteString.EMPTY);
    }

    @Test
    void delegatesToCorrectKeyProperty() {
        subject.key(genesis);

        verify(accountsLedger).get(genesis, KEY);
    }

    @Test
    void delegatesToCorrectMaxAutomaticAssociationsProperty() {
        subject.maxAutomaticAssociations(genesis);
        verify(accountsLedger).get(genesis, MAX_AUTOMATIC_ASSOCIATIONS);

        subject.setMaxAutomaticAssociations(genesis, 10);
        verify(accountsLedger).set(genesis, MAX_AUTOMATIC_ASSOCIATIONS, 10);
    }

    @Test
    void delegatesToCorrectAlreadyUsedAutomaticAssociationProperty() {
        subject.alreadyUsedAutomaticAssociations(genesis);
        verify(accountsLedger).get(genesis, USED_AUTOMATIC_ASSOCIATIONS);

        subject.setAlreadyUsedAutomaticAssociations(genesis, 7);
        verify(accountsLedger).set(genesis, USED_AUTOMATIC_ASSOCIATIONS, 7);
    }

    @Test
    void throwsOnUnderfundedCreate() {
        assertThrows(
                InsufficientFundsException.class,
                () -> subject.create(rand, RAND_BALANCE + 1, noopCustomizer));
    }

    @Test
    void performsFundedCreate() {
        final var customizer = mock(HederaAccountCustomizer.class);
        given(accountsLedger.existsPending(IdUtils.asAccount(String.format("0.0.%d", NEXT_ID))))
                .willReturn(true);

        final var created = subject.create(rand, 1_000L, customizer);

        assertEquals(NEXT_ID, created.getAccountNum());
        verify(accountsLedger).set(rand, BALANCE, RAND_BALANCE - 1_000L);
        verify(accountsLedger).create(created);
        verify(accountsLedger).set(created, BALANCE, 1_000L);
        verify(customizer).customize(created, accountsLedger);
    }

    @Test
    void performsUnconditionalSpawn() {
        final var customizer = mock(HederaAccountCustomizer.class);
        final var contract = asAccount("1.2.3");
        final var balance = 1_234L;
        given(accountsLedger.existsPending(contract)).willReturn(true);

        subject.spawn(contract, balance, customizer);

        verify(accountsLedger).create(contract);
        verify(accountsLedger).set(contract, BALANCE, balance);
        verify(customizer).customize(contract, accountsLedger);
    }

    @Test
    void deletesGivenAccount() {
        subject.delete(rand, misc);

        verify(accountsLedger).set(rand, BALANCE, 0L);
        verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + RAND_BALANCE);
        verify(accountsLedger).set(rand, IS_DELETED, true);
    }

    @Test
    void throwsOnCustomizingDeletedAccount() {
        assertThrows(
                DeletedAccountException.class, () -> subject.customize(deleted, noopCustomizer));
    }

    @Test
    void customizesGivenAccount() {
        final var customizer = mock(HederaAccountCustomizer.class);

        subject.customize(rand, customizer);

        verify(customizer).customize(rand, accountsLedger);
    }

    @Test
    void customizesPotentiallyDeletedAccount() {
        final var customizer = mock(HederaAccountCustomizer.class);

        subject.customizePotentiallyDeleted(deleted, customizer);

        verify(customizer).customize(deleted, accountsLedger);
    }

    @Test
    void makesPossibleAdjustment() {
        final var amount = -1 * GENESIS_BALANCE / 2;

        subject.adjustBalance(genesis, amount);

        verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE + amount);
    }

    @Test
    void throwsOnNegativeBalance() {
        final var overdraftAdjustment = -1 * GENESIS_BALANCE - 1;

        final var e =
                assertThrows(
                        InsufficientFundsException.class,
                        () -> subject.adjustBalance(genesis, overdraftAdjustment));

        assertEquals(messageFor(genesis, overdraftAdjustment), e.getMessage());
        verify(accountsLedger, never()).set(any(), any(), any());
    }

    @Test
    void forwardsGetBalanceCorrectly() {
        final var balance = subject.getBalance(genesis);

        assertEquals(GENESIS_BALANCE, balance);
    }

    @Test
    void forwardsTransactionalSemantics() {
        subject.setTokenRelsLedger(null);
        final var inOrder = inOrder(accountsLedger, mutableEntityAccess);
        given(sideEffectsTracker.getNetTrackedHbarChanges()).willReturn(new CurrencyAdjustments());

        subject.begin();
        subject.commit();
        subject.begin();
        subject.rollback();

        inOrder.verify(accountsLedger).begin();
        inOrder.verify(mutableEntityAccess).startAccess();
        inOrder.verify(accountsLedger).commit();
        inOrder.verify(accountsLedger).begin();
        inOrder.verify(mutableEntityAccess).startAccess();
        inOrder.verify(accountsLedger).rollback();
    }
}
