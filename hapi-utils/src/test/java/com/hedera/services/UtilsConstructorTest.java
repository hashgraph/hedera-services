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
package com.hedera.services;

import com.hedera.services.keys.Ed25519Utils;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.proto.utils.SignatureGenerator;
import com.hedera.services.sysfiles.ParsingUtils;
import com.hedera.services.sysfiles.domain.throttling.HapiThrottleUtils;
import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hedera.services.sysfiles.validation.ErrorCodeUtils;
import com.hedera.services.sysfiles.validation.ExpectedCustomThrottles;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.fee.ConsensusServiceFeeBuilder;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UtilsConstructorTest {
    private static final Set<Class<?>> toBeTested =
            new HashSet<>(
                    Arrays.asList(
                            HapiThrottleUtils.class,
                            ParsingUtils.class,
                            CommonUtils.class,
                            Ed25519Utils.class,
                            ByteStringUtils.class,
                            SignatureGenerator.class,
                            ThrottlesJsonToProtoSerde.class,
                            ErrorCodeUtils.class,
                            ExpectedCustomThrottles.class,
                            RequestBuilder.class,
                            ConsensusServiceFeeBuilder.class));

    @Test
    void throwsInConstructor() {
        for (final var clazz : toBeTested) {
            assertFor(clazz);
        }
    }

    private static final String UNEXPECTED_THROW =
            "Unexpected `%s` was thrown in `%s` constructor!";
    private static final String NO_THROW = "No exception was thrown in `%s` constructor!";

    private void assertFor(final Class<?> clazz) {
        try {
            final var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);

            constructor.newInstance();
        } catch (final InvocationTargetException expected) {
            final var cause = expected.getCause();
            Assertions.assertTrue(
                    cause instanceof UnsupportedOperationException,
                    String.format(UNEXPECTED_THROW, cause, clazz));
            return;
        } catch (final Exception e) {
            Assertions.fail(String.format(UNEXPECTED_THROW, e, clazz));
        }
        Assertions.fail(String.format(NO_THROW, clazz));
    }
}
