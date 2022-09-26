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
package com.hedera.services.sigs.order;

import static com.hedera.services.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeOrderResultFactoryTest {
    private CodeOrderResultFactory subject = CODE_ORDER_RESULT_FACTORY;

    @Test
    void createsNewResultForValidOrder() {
        // given:
        final List<JKey> pretendKeys =
                List.of(new JEd25519Key("IMAGINARY".getBytes(StandardCharsets.UTF_8)));

        // when:
        final var ans = subject.forValidOrder(pretendKeys);

        // then:
        assertSame(pretendKeys, ans.getOrderedKeys());
    }

    @Test
    void errorReportsReturnSingletons() {
        // expect:
        assertSame(CodeOrderResultFactory.INVALID_ACCOUNT_RESULT, subject.forInvalidAccount());
        assertSame(CodeOrderResultFactory.GENERAL_ERROR_RESULT, subject.forGeneralError());
        assertSame(
                CodeOrderResultFactory.GENERAL_PAYER_ERROR_RESULT, subject.forGeneralPayerError());
        assertSame(CodeOrderResultFactory.MISSING_ACCOUNT_RESULT, subject.forMissingAccount());
        assertSame(CodeOrderResultFactory.MISSING_FILE_RESULT, subject.forMissingFile());
        assertSame(CodeOrderResultFactory.MISSING_CONTRACT_RESULT, subject.forInvalidContract());
        assertSame(
                CodeOrderResultFactory.IMMUTABLE_CONTRACT_RESULT, subject.forImmutableContract());
        assertSame(CodeOrderResultFactory.MISSING_TOPIC_RESULT, subject.forMissingTopic());
        assertSame(
                CodeOrderResultFactory.INVALID_AUTORENEW_RESULT,
                subject.forInvalidAutoRenewAccount());
        assertSame(CodeOrderResultFactory.MISSING_TOKEN_RESULT, subject.forMissingToken());
        assertSame(CodeOrderResultFactory.MISSING_SCHEDULE_RESULT, subject.forMissingSchedule());
        assertSame(CodeOrderResultFactory.MISSING_SCHEDULE_RESULT, subject.forMissingSchedule());
        assertSame(
                CodeOrderResultFactory.UNRESOLVABLE_SIGNERS_RESULT,
                subject.forUnresolvableRequiredSigners());
        assertSame(
                CodeOrderResultFactory.UNSCHEDULABLE_TRANSACTION_RESULT,
                subject.forUnschedulableTxn());
        assertSame(
                CodeOrderResultFactory.INVALID_FEE_COLLECTOR_RESULT,
                subject.forInvalidFeeCollector());
        assertSame(
                CodeOrderResultFactory.MISSING_ALLOWANCE_OWNER_RESULT,
                subject.forInvalidAllowanceOwner());
    }
}
