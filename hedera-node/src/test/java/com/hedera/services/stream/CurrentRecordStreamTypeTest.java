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
package com.hedera.services.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.properties.ActiveVersions;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.SemanticVersions;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class CurrentRecordStreamTypeTest {
    private static final SemanticVersion pretendSemVer =
            SemanticVersion.newBuilder()
                    .setMajor(1)
                    .setMinor(2)
                    .setPatch(4)
                    .setPre("zeta.123")
                    .setBuild("2b26be40")
                    .build();
    private static final int[] expectedV5Header =
            new int[] {
                5, pretendSemVer.getMajor(), pretendSemVer.getMinor(), pretendSemVer.getPatch()
            };
    private static final int[] expectedV6Header =
            new int[] {
                6, pretendSemVer.getMajor(), pretendSemVer.getMinor(), pretendSemVer.getPatch()
            };

    @Mock private ActiveVersions activeVersions;
    @Mock private SemanticVersions semanticVersions;
    @Mock private GlobalDynamicProperties dynamicProperties;

    @LoggingSubject private CurrentRecordStreamType subject;
    @LoggingTarget private LogCaptor logCaptor;

    @BeforeEach
    void setUp() {
        subject = new CurrentRecordStreamType(semanticVersions, dynamicProperties);
    }

    @Test
    void returnsCurrentStreamTypeFromResource() {
        given(semanticVersions.getDeployed()).willReturn(activeVersions);
        given(activeVersions.protoSemVer()).willReturn(pretendSemVer);
        given(dynamicProperties.recordFileVersion()).willReturn(5);

        final var header = subject.getFileHeader();
        assertArrayEquals(expectedV5Header, header);
        assertSame(header, subject.getFileHeader());
    }

    @Test
    void returnsCurrentStreamTypeFromResourceV6() {
        given(semanticVersions.getDeployed()).willReturn(activeVersions);
        given(activeVersions.protoSemVer()).willReturn(pretendSemVer);
        given(dynamicProperties.recordFileVersion()).willReturn(6);

        final var header = subject.getFileHeader();

        assertArrayEquals(expectedV6Header, header);
        assertSame(header, subject.getFileHeader());
    }

    @Test
    void failsFastIfProtoVersionWasNotLoadedFromResource() {
        given(semanticVersions.getDeployed()).willReturn(activeVersions);
        given(activeVersions.protoSemVer()).willReturn(SemanticVersion.getDefaultInstance());

        subject.getFileHeader();

        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Failed to load")));
    }

    @Test
    void sigFileHeaderReturnsVersionFromDynamicProperties() {
        given(dynamicProperties.recordSignatureFileVersion()).willReturn(6);

        final var header = subject.getSigFileHeader();

        assertArrayEquals(new byte[] {6}, header);
    }
}
