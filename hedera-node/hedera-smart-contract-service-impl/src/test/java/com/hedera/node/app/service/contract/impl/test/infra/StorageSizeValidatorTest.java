/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.infra;

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertExhaustsResourceLimit;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.infra.StorageSizeValidator;
import com.hedera.node.app.service.contract.impl.state.StorageSizeChange;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageSizeValidatorTest {
    private static final long PRETEND_MAX_AGGREGATE = 123456L;

    @Mock
    private HederaOperations extWorldScope;

    private StorageSizeValidator subject;

    @Test
    void throwsOnTooManyAggregatePairs() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("contracts.maxKvPairs.aggregate", PRETEND_MAX_AGGREGATE)
                .getOrCreateConfig();

        subject = new StorageSizeValidator(config.getConfigData(ContractsConfig.class));

        assertExhaustsResourceLimit(
                () -> subject.assertValid(PRETEND_MAX_AGGREGATE + 1, extWorldScope, List.of()),
                MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED);
    }

    @Test
    void throwsOnTooManyIndividualPairs() {
        final var pretendMaxIndividual = 666L;
        final var underLimitNumber = 123L;
        final var overLimitNumber = 321L;
        final var config = HederaTestConfigBuilder.create()
                .withValue("contracts.maxKvPairs.individual", pretendMaxIndividual)
                .getOrCreateConfig();
        final var sizeChanges =
                List.of(new StorageSizeChange(underLimitNumber, 1, 2), new StorageSizeChange(overLimitNumber, 0, 1));
        given(extWorldScope.getOriginalSlotsUsed(underLimitNumber)).willReturn(pretendMaxIndividual - 1);
        given(extWorldScope.getOriginalSlotsUsed(overLimitNumber)).willReturn(pretendMaxIndividual);

        subject = new StorageSizeValidator(config.getConfigData(ContractsConfig.class));

        assertExhaustsResourceLimit(
                () -> subject.assertValid(PRETEND_MAX_AGGREGATE, extWorldScope, sizeChanges),
                MAX_CONTRACT_STORAGE_EXCEEDED);
    }
}
