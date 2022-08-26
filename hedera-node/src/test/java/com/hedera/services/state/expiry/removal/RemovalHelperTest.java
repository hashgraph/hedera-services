/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry.removal;

import static com.hedera.services.state.expiry.classification.ClassificationWork.CLASSIFICATION_WORK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.ExpiryRecordsHelper;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemovalHelperTest {
    private final CryptoGcOutcome finishedReturns =
            new CryptoGcOutcome(
                    FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS,
                    NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                    true);
    private final CryptoGcOutcome unfinishedReturns =
            new CryptoGcOutcome(
                    FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                    NonFungibleTreasuryReturns.UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                    false);
    private MerkleMap<EntityNum, MerkleAccount> accounts;
    private final MockGlobalDynamicProps properties = new MockGlobalDynamicProps();
    @Mock private ContractGC contractGC;
    @Mock private AccountGC accountGC;
    @Mock private ExpiryRecordsHelper recordsHelper;
    @Mock private ExpiryThrottle expiryThrottle;

    private EntityLookup lookup;
    private ClassificationWork classifier;
    private RemovalHelper subject;

    @BeforeEach
    void setUp() {
        accounts = new MerkleMap<>();
        accounts.put(EntityNum.fromLong(expiredDeletedAccountNum), expiredDeletedAccount);
        accounts.put(EntityNum.fromLong(expiredDeletedContractNum), expiredDeletedContract);
        lookup = new EntityLookup(() -> accounts);
        classifier = new ClassificationWork(properties, lookup, expiryThrottle);

        subject = new RemovalHelper(classifier, properties, contractGC, accountGC, recordsHelper);
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.disableAutoRenew();
        var result = subject.tryToRemoveAccount(EntityNum.fromLong(nonExpiredAccountNum));
        assertEquals(EntityProcessResult.NOTHING_TO_DO, result);

        properties.disableContractAutoRenew();
        result = subject.tryToRemoveContract(EntityNum.fromLong(nonExpiredAccountNum));
        assertEquals(EntityProcessResult.NOTHING_TO_DO, result);
    }

    @Test
    void removesAccountAsExpected() {
        properties.enableAutoRenew();
        final var expiredNum = EntityNum.fromLong(expiredDeletedAccountNum);

        given(accountGC.expireBestEffort(expiredNum, expiredDeletedAccount))
                .willReturn(finishedReturns);
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);

        classifier.classify(expiredNum, now);

        var result = subject.tryToRemoveAccount(expiredNum);

        verify(recordsHelper).streamCryptoRemovalStep(false, expiredNum, null, finishedReturns);
        assertEquals(EntityProcessResult.DONE, result);
    }

    @Test
    void doesntExternalizeNoopGc() {
        properties.enableAutoRenew();
        final var expiredNum = EntityNum.fromLong(expiredDeletedAccountNum);

        given(accountGC.expireBestEffort(expiredNum, expiredDeletedAccount))
                .willReturn(unfinishedReturns);
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);

        classifier.classify(expiredNum, now);

        var result = subject.tryToRemoveAccount(expiredNum);

        verifyNoInteractions(recordsHelper);
        assertEquals(EntityProcessResult.STILL_MORE_TO_DO, result);
    }

    @Test
    void removesContractAsExpected() {
        properties.enableAutoRenew();
        final var expiredNum = EntityNum.fromLong(expiredDeletedContractNum);

        given(contractGC.expireBestEffort(expiredNum, expiredDeletedContract)).willReturn(true);
        given(accountGC.expireBestEffort(expiredNum, expiredDeletedContract))
                .willReturn(finishedReturns);
        given(expiryThrottle.allow(eq(CLASSIFICATION_WORK), any(Instant.class))).willReturn(true);
        final var autoRenewId = EntityId.fromNum(12345);
        expiredDeletedContract.setAutoRenewAccount(autoRenewId);

        classifier.classify(expiredNum, now);

        var result = subject.tryToRemoveContract(expiredNum);

        verify(recordsHelper)
                .streamCryptoRemovalStep(true, expiredNum, autoRenewId, finishedReturns);
        assertEquals(EntityProcessResult.DONE, result);
    }

    private final Instant now = Instant.ofEpochSecond(1_234_567L);
    private final long nonExpiredAccountNum = 1002L;
    private final long expiredDeletedAccountNum = 1003L;
    private final long expiredDeletedContractNum = 1004L;

    private final MerkleAccount expiredDeletedAccount =
            MerkleAccountFactory.newAccount()
                    .balance(0)
                    .deleted(true)
                    .alias(ByteString.copyFromUtf8("cccc"))
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
    private final MerkleAccount expiredDeletedContract =
            MerkleAccountFactory.newAccount()
                    .isSmartContract(true)
                    .balance(0)
                    .deleted(true)
                    .alias(ByteString.copyFromUtf8("cccc"))
                    .expirationTime(now.getEpochSecond() - 1)
                    .get();
}
