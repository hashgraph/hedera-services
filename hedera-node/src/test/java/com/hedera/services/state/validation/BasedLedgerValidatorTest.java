/*
 * Copyright (C) 2020-2021 Hedera Hashgraph, LLC
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
package com.hedera.services.state.validation;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BasedLedgerValidatorTest {
    private long shard = 1;
    private long realm = 2;

    MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();

    HederaNumbers hederaNums;
    PropertySource properties;
    GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

    BasedLedgerValidator subject;

    @BeforeEach
    private void setup() {
        hederaNums = mock(HederaNumbers.class);
        given(hederaNums.realm()).willReturn(realm);
        given(hederaNums.shard()).willReturn(shard);

        properties = mock(PropertySource.class);
        given(properties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(100L);

        subject = new BasedLedgerValidator(properties, dynamicProperties);
    }

    @Test
    void recognizesRightFloat() throws NegativeAccountBalanceException {
        // given:
        accounts.put(EntityNum.fromLong(1L), expectedWith(50L));
        accounts.put(EntityNum.fromLong(2L), expectedWith(50L));

        // expect:
        assertDoesNotThrow(() -> subject.validate(accounts));
    }

    @Test
    void recognizesWrongFloat() throws NegativeAccountBalanceException {
        // given:
        accounts.put(EntityNum.fromLong(1L), expectedWith(50L));
        accounts.put(EntityNum.fromLong(2L), expectedWith(51L));

        // expect:
        assertThrows(IllegalStateException.class, () -> subject.validate(accounts));
    }

    @Test
    void recognizesExcessFloat() throws NegativeAccountBalanceException {
        // given:
        accounts.put(EntityNum.fromLong(1L), expectedWith(Long.MAX_VALUE));
        accounts.put(EntityNum.fromLong(2L), expectedWith(51L));

        // expect:
        assertThrows(IllegalStateException.class, () -> subject.validate(accounts));
    }

    @Test
    void doesntThrowWithValidIds() throws NegativeAccountBalanceException {
        // given:
        accounts.put(EntityNum.fromLong(3L), expectedWith(100L));

        // expect:
        assertDoesNotThrow(() -> subject.validate(accounts));
    }

    @Test
    void throwsOnIdWithNumTooSmall() throws NegativeAccountBalanceException {
        // given:
        accounts.put(EntityNum.fromLong(0L), expectedWith(100L));

        // expect:
        assertThrows(IllegalStateException.class, () -> subject.validate(accounts));
    }

    @Test
    void throwsOnIdWithNumTooLarge() throws NegativeAccountBalanceException {
        // given:
        accounts.put(EntityNum.fromLong(dynamicProperties.maxAccountNum() + 1), expectedWith(100L));

        // expect:
        assertThrows(IllegalStateException.class, () -> subject.validate(accounts));
    }

    private MerkleAccount expectedWith(long balance) throws NegativeAccountBalanceException {
        MerkleAccount hAccount =
                new HederaAccountCustomizer()
                        .isReceiverSigRequired(false)
                        .proxy(MISSING_ENTITY_ID)
                        .isDeleted(false)
                        .expiry(1_234_567L)
                        .memo("")
                        .isSmartContract(false)
                        .customizing(new MerkleAccount());
        hAccount.setBalance(balance);
        return hAccount;
    }
}
