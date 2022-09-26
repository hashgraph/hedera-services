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
package com.hedera.services.txns.contract.helpers;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateCustomizerFactoryTest {
    private long curExpiry = 1_234_567L;
    private long newExpiry = 2 * curExpiry;
    private ContractID target = IdUtils.asContract("0.0.1234");
    private Key targetKey = Key.newBuilder().setContractID(target).build();
    private Duration newAutoRenew = Duration.newBuilder().setSeconds(654_321L).build();
    private AccountID newProxy = IdUtils.asAccount("0.0.4321");
    private String newMemo = "The commonness of thoughts and images";
    private Key newAdminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asKey();
    private AccountID newAutoRenewAccount = IdUtils.asAccount("0.0.12345");
    private int maxAutoAssociations = 25;
    private AccountID newStakedId = IdUtils.asAccount("0.0.12345");

    private UpdateCustomizerFactory subject = new UpdateCustomizerFactory();

    @Mock private OptionValidator optionValidator;

    @Test
    void makesExpectedChanges() {
        // setup:
        final var newExpiryTime = Timestamp.newBuilder().setSeconds(newExpiry).build();

        // given:
        var mutableContract =
                MerkleAccountFactory.newContract()
                        .accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
                        .get();
        // and:
        var op =
                ContractUpdateTransactionBody.newBuilder()
                        .setContractID(target)
                        .setAdminKey(newAdminKey)
                        .setAutoRenewPeriod(newAutoRenew)
                        .setProxyAccountID(newProxy)
                        .setStakedNodeId(newStakedId.getAccountNum())
                        .setMemoWrapper(StringValue.newBuilder().setValue(newMemo))
                        .setExpirationTime(newExpiryTime)
                        .setAutoRenewAccountId(newAutoRenewAccount)
                        .setMaxAutomaticTokenAssociations(Int32Value.of(maxAutoAssociations))
                        .build();

        given(optionValidator.isValidExpiry(newExpiryTime)).willReturn(true);

        // when:
        var result = subject.customizerFor(mutableContract, optionValidator, op);
        // and when:
        mutableContract = result.getLeft().get().customizing(mutableContract);

        // then:
        assertEquals(newAdminKey, MiscUtils.asKeyUnchecked(mutableContract.getAccountKey()));
        assertEquals(newAutoRenew.getSeconds(), mutableContract.getAutoRenewSecs());
        assertEquals(newExpiry, mutableContract.getExpiry());
        assertEquals(newMemo, mutableContract.getMemo());
        assertEquals(newAutoRenewAccount, mutableContract.getAutoRenewAccount().toGrpcAccountId());
        assertEquals(maxAutoAssociations, mutableContract.getMaxAutomaticAssociations());
        assertEquals(null, mutableContract.getProxy());
        assertEquals(-12346, mutableContract.getStakedId());
        assertEquals(false, mutableContract.isDeclinedReward());
    }

    @Test
    void rejectsInvalidExpiryMakesExpectedChanges() {
        // setup:
        final var newExpiryTime = Timestamp.newBuilder().setSeconds(newExpiry).build();

        // given:
        var mutableContract =
                MerkleAccountFactory.newContract()
                        .accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
                        .get();
        // and:
        var op =
                ContractUpdateTransactionBody.newBuilder().setExpirationTime(newExpiryTime).build();

        // when:
        var result = subject.customizerFor(mutableContract, optionValidator, op);

        // then:
        assertTrue(result.getLeft().isEmpty());
        assertEquals(INVALID_EXPIRATION_TIME, result.getRight());
    }

    @Test
    void permitsMakingImmutableWithSentinel() {
        // given:
        var mutableContract =
                MerkleAccountFactory.newContract()
                        .accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
                        .get();
        // and:
        var op =
                ContractUpdateTransactionBody.newBuilder()
                        .setContractID(target)
                        .setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY)
                        .build();

        // when:
        var result = subject.customizerFor(mutableContract, optionValidator, op);
        // and when:
        mutableContract = result.getLeft().get().customizing(mutableContract);

        // then:
        assertEquals(targetKey, MiscUtils.asKeyUnchecked(mutableContract.getAccountKey()));
    }

    @Test
    void disallowsInvalidKey() {
        // given:
        var mutableContract =
                MerkleAccountFactory.newContract()
                        .accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
                        .get();
        // and:
        var op =
                ContractUpdateTransactionBody.newBuilder()
                        .setAdminKey(
                                Key.newBuilder().setEd25519(ByteString.copyFrom("1".getBytes())))
                        .build();

        // when:
        var result = subject.customizerFor(mutableContract, optionValidator, op);

        // then:
        assertTrue(result.getLeft().isEmpty());
        assertEquals(INVALID_ADMIN_KEY, result.getRight());
    }

    @Test
    void disallowsExplicitContractKey() {
        // given:
        var mutableContract =
                MerkleAccountFactory.newContract()
                        .accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
                        .get();
        // and:
        var op =
                ContractUpdateTransactionBody.newBuilder()
                        .setAdminKey(Key.newBuilder().setContractID(target))
                        .build();

        // when:
        var result = subject.customizerFor(mutableContract, optionValidator, op);

        // then:
        assertTrue(result.getLeft().isEmpty());
        assertEquals(INVALID_ADMIN_KEY, result.getRight());
    }

    @Test
    void refusesToShortenLifetime() {
        // setup:
        long then = curExpiry;

        // given:
        var mutableContract =
                MerkleAccountFactory.newContract()
                        .accountKeys(MISC_ADMIN_KT.asJKeyUnchecked())
                        .expirationTime(then)
                        .get();
        // and:
        var op =
                ContractUpdateTransactionBody.newBuilder()
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(then - 1).build())
                        .build();

        // when:
        var result = subject.customizerFor(mutableContract, optionValidator, op);

        // then:
        assertTrue(result.getLeft().isEmpty());
        assertEquals(EXPIRATION_REDUCTION_NOT_ALLOWED, result.getRight());
    }

    @Test
    void refusesToCustomizeNonExpiryChangeToImmutableContract() {
        // given:
        var immutableContract =
                MerkleAccountFactory.newContract().accountKeys(new JContractIDKey(0, 0, 2)).get();
        // and:
        var op =
                ContractUpdateTransactionBody.newBuilder()
                        .setProxyAccountID(IdUtils.asAccount("0.0.1234"))
                        .build();

        // when:
        var result = subject.customizerFor(immutableContract, optionValidator, op);

        // then:
        assertTrue(result.getLeft().isEmpty());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, result.getRight());
    }

    @Test
    void accountMutabilityUnderstood() {
        // given:
        var contractWithMissingKey = MerkleAccountFactory.newContract().get();
        var contractWithNotionalKey =
                MerkleAccountFactory.newContract().accountKeys(new JContractIDKey(0, 0, 2)).get();
        var contractWithEd25519Key =
                MerkleAccountFactory.newContract()
                        .accountKeys(TxnHandlingScenario.FIRST_TOKEN_SENDER_KT.asJKeyUnchecked())
                        .get();

        // expect:
        assertFalse(subject.isMutable(contractWithMissingKey));
        assertFalse(subject.isMutable(contractWithNotionalKey));
        assertTrue(subject.isMutable(contractWithEd25519Key));
    }

    @Test
    void understandsNonExpiryEffects() {
        // expect:
        assertFalse(
                subject.onlyAffectsExpiry(
                        ContractUpdateTransactionBody.newBuilder()
                                .setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY)
                                .build()));
        assertFalse(
                subject.onlyAffectsExpiry(
                        ContractUpdateTransactionBody.newBuilder()
                                .setMemoWrapper(
                                        StringValue.newBuilder()
                                                .setValue("You're not from these parts, are you?"))
                                .build()));
        assertFalse(
                subject.onlyAffectsExpiry(
                        ContractUpdateTransactionBody.newBuilder()
                                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(123L))
                                .build()));
        assertFalse(
                subject.onlyAffectsExpiry(
                        ContractUpdateTransactionBody.newBuilder()
                                .setFileID(IdUtils.asFile("0.0.4321"))
                                .build()));
        assertFalse(
                subject.onlyAffectsExpiry(
                        ContractUpdateTransactionBody.newBuilder()
                                .setProxyAccountID(IdUtils.asAccount("0.0.1234"))
                                .build()));
        assertTrue(
                subject.onlyAffectsExpiry(
                        ContractUpdateTransactionBody.newBuilder()
                                .setExpirationTime(Timestamp.newBuilder().setSeconds(1_234_567L))
                                .build()));
    }

    @Test
    void understandsMemoImpact() {
        // expect:
        assertFalse(subject.affectsMemo(ContractUpdateTransactionBody.getDefaultInstance()));
        assertTrue(
                subject.affectsMemo(
                        ContractUpdateTransactionBody.newBuilder().setMemo("Hi!").build()));
        assertTrue(
                subject.affectsMemo(
                        ContractUpdateTransactionBody.newBuilder()
                                .setMemoWrapper(
                                        StringValue.newBuilder()
                                                .setValue("Interesting to see you here!"))
                                .build()));
    }
}
