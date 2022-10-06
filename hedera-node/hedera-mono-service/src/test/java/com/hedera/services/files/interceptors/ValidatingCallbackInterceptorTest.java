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
package com.hedera.services.files.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hederahashgraph.api.proto.java.FileID;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidatingCallbackInterceptorTest {
    private HFileMeta attr;
    private byte[] BYTES = "SOMETHING".getBytes();

    int applicablePriority = 123;
    long actualNum = 66;
    String numProperty = "hedera.imaginaryFile.num";
    Consumer<byte[]> postUpdateCb;
    Predicate<byte[]> validator;
    PropertySource properties;

    FileID notTarget = FileID.newBuilder().setFileNum(actualNum - 1).build();
    FileID target = FileID.newBuilder().setFileNum(actualNum).build();

    ValidatingCallbackInterceptor subject;

    @BeforeEach
    void setup() {
        attr = new HFileMeta(false, new JContractIDKey(1, 2, 3), Instant.now().getEpochSecond());

        validator = mock(Predicate.class);
        postUpdateCb = mock(Consumer.class);

        properties = mock(PropertySource.class);
        given(properties.getLongProperty(numProperty)).willReturn(actualNum);

        subject =
                new ValidatingCallbackInterceptor(
                        applicablePriority, numProperty, properties, postUpdateCb, validator);
    }

    @Test
    void hasExpectedRelevanceAndPriority() {
        // expect:
        assertTrue(subject.priorityForCandidate(notTarget).isEmpty());
        // and:
        assertEquals(applicablePriority, subject.priorityForCandidate(target).getAsInt());
    }

    @Test
    void rubberstampsPreDelete() {
        // expect:
        assertEquals(FeeSchedulesManager.YES_VERDICT, subject.preDelete(notTarget));
        assertEquals(FeeSchedulesManager.YES_VERDICT, subject.preDelete(target));
    }

    @Test
    void rubberstampsPreUpdate() {
        // expect:
        assertEquals(FeeSchedulesManager.YES_VERDICT, subject.preUpdate(notTarget, BYTES));
        assertEquals(FeeSchedulesManager.YES_VERDICT, subject.preUpdate(target, BYTES));
    }

    @Test
    void rubberstampsAttrChange() {
        // expect:
        assertEquals(FeeSchedulesManager.YES_VERDICT, subject.preAttrChange(notTarget, attr));
        assertEquals(FeeSchedulesManager.YES_VERDICT, subject.preAttrChange(target, attr));
    }

    @Test
    void postUpdateInvokesCbForTarget() {
        given(validator.test(any())).willReturn(true);

        // when:
        subject.postUpdate(target, BYTES);

        // then:
        verify(postUpdateCb).accept(argThat(bytes -> Arrays.equals(BYTES, bytes)));
        verify(validator).test(argThat(bytes -> Arrays.equals(BYTES, bytes)));
    }

    @Test
    void postUpdateInvokesCbForTargetOnlyIfValid() {
        given(validator.test(any())).willReturn(false);

        // when:
        subject.postUpdate(target, BYTES);

        // then:
        verify(postUpdateCb, never()).accept(any());
        verify(validator).test(argThat(bytes -> Arrays.equals(BYTES, bytes)));
    }

    @Test
    void postUpdateDoesntInvokesCbForNonTarget() {
        // when:
        subject.postUpdate(notTarget, BYTES);

        // then:
        verify(postUpdateCb, never()).accept(any());
    }
}
