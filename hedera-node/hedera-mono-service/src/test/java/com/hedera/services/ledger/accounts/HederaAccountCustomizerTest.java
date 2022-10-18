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
package com.hedera.services.ledger.accounts;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HederaAccountCustomizerTest {
    HederaAccountCustomizer subject = new HederaAccountCustomizer();

    @Test
    void hasExpectedOptionProps() {
        // given:
        Map<Option, AccountProperty> optionProperties = subject.getOptionProperties();

        // expect:
        Arrays.stream(Option.class.getEnumConstants())
                .forEach(
                        option ->
                                assertEquals(
                                        AccountProperty.valueOf(option.toString()),
                                        optionProperties.get(option)));
    }

    @Test
    void canCustomizeAlias() {
        final var target = new MerkleAccount();
        final var alias = ByteString.copyFromUtf8("FAKE");
        subject.alias(alias).customizing(target);
        assertEquals(alias, target.getAlias());
    }

    @Test
    void customizesSyntheticContractCreation() {
        final var memo = "Inherited";
        final var stakedId = 4L;
        final var autoRenew = 7776001L;
        final var expiry = 1_234_567L;
        final var autoRenewAccount = new EntityId(0, 0, 5);

        final var customizer =
                new HederaAccountCustomizer()
                        .memo(memo)
                        .stakedId(stakedId)
                        .autoRenewPeriod(autoRenew)
                        .expiry(expiry)
                        .isSmartContract(true)
                        .autoRenewAccount(autoRenewAccount)
                        .maxAutomaticAssociations(10);

        final var op = ContractCreateTransactionBody.newBuilder();
        customizer.customizeSynthetic(op);

        assertEquals(memo, op.getMemo());
        assertEquals(STATIC_PROPERTIES.scopedAccountWith(stakedId), op.getStakedAccountId());
        assertEquals(autoRenew, op.getAutoRenewPeriod().getSeconds());
        assertEquals(false, op.getDeclineReward());
    }

    @Test
    void negativeNumMinusOneForStakedIdIsNodeID() {
        final var memo = "Inherited";
        final var stakedId = -4L;
        final var autoRenew = 7776001L;
        final var expiry = 1_234_567L;
        final var autoRenewAccount = new EntityId(0, 0, 5);

        final var customizer =
                new HederaAccountCustomizer()
                        .memo(memo)
                        .stakedId(stakedId)
                        .autoRenewPeriod(autoRenew)
                        .expiry(expiry)
                        .isSmartContract(true)
                        .isDeclinedReward(true)
                        .autoRenewAccount(autoRenewAccount);

        final var op = ContractCreateTransactionBody.newBuilder();
        customizer.customizeSynthetic(op);

        assertEquals(memo, op.getMemo());
        assertEquals(-1 * stakedId - 1, op.getStakedNodeId());
        assertEquals(autoRenew, op.getAutoRenewPeriod().getSeconds());
        assertEquals(autoRenewAccount.toGrpcAccountId(), op.getAutoRenewAccountId());
        assertEquals(true, op.getDeclineReward());
    }

    @Test
    void noopCustomizationIsSafe() {
        final var customizer = new HederaAccountCustomizer().isSmartContract(true);
        final var op = ContractCreateTransactionBody.newBuilder();

        assertDoesNotThrow(() -> customizer.customizeSynthetic(op));
    }
}
