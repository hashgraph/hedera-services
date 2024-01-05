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

package com.hedera.node.app.service.mono.txns.crypto.validators;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoCreateChecksTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private OptionValidator validator;

    @Mock
    private Supplier<AccountStorageAdapter> accounts;

    @Mock
    private NodeInfo nodeInfo;

    @Mock
    private AliasManager aliasManager;

    private CryptoCreateChecks subject;

    @BeforeEach
    void setup() {
        subject = new CryptoCreateChecks(dynamicProperties, validator, accounts, nodeInfo, aliasManager);
    }

    @Test
    void rejectsTooManyAutoAssociations() {
        given(dynamicProperties.maxAllowedAutoAssociations()).willReturn(5000);
        given(validator.memoCheck(anyString())).willReturn(OK);
        given(validator.hasGoodEncoding(any())).willReturn(true);
        given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

        final var op = CryptoCreateTransactionBody.newBuilder()
                .setKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")))
                .setMaxAutomaticTokenAssociations(5001)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(7776000L).build())
                .build();

        final var status = subject.cryptoCreateValidation(op);

        assertEquals(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT, status);
    }
}
