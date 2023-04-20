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

package com.hedera.node.app.service.token.impl.test.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.service.token.impl.records.CreateAccountRecordBuilder;
import org.junit.jupiter.api.Test;

public class CreateAccountRecordBuilderTest {
    @Test
    void setterAndGetterWorks() {
        var subject = new CreateAccountRecordBuilder();

        subject.setCreatedAccount(1L);
        assertThat(subject.getCreatedAccount()).isEqualTo(1L);
    }

    @Test
    void getterWithoutSetFails() {
        var subject = new CreateAccountRecordBuilder();

        assertThatThrownBy(() -> subject.getCreatedAccount(), "No new account number was recorded");
    }

    @Test
    void selfReturnsSameObject() {
        var subject = new CreateAccountRecordBuilder();

        assertThat(subject.self()).isSameAs(subject);
    }
}
