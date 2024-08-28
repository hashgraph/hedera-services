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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.create;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator.BURN_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_TOKEN_WITH_METADATA;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EXPECTED_FIXED_CUSTOM_FEES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelector;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHtsAttemptWithSelectorAndCustomConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.ClassicCreatesCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CreateTranslatorTest extends CallTestBase {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    Configuration configuration;

    private CreateDecoder decoder = new CreateDecoder();

    private CreateTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new CreateTranslator(decoder);
    }

    @Test
    void matchesCreateFungibleTokenV1() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_FUNGIBLE_TOKEN_V1,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateFungibleTokenV2() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_FUNGIBLE_TOKEN_V2,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateFungibleTokenV3() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_FUNGIBLE_TOKEN_V3,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateFungibleTokenWithMetadata() {
        enableConfig();
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                CREATE_FUNGIBLE_TOKEN_WITH_METADATA,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateFungibleTokenWithCustomFeesV1() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateFungibleTokenWithCustomFeesV2() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateFungibleTokenWithCustomFeesV3() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateFungibleTokenWithMetadataAndCustomFees() {
        enableConfig();
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenV1() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_NON_FUNGIBLE_TOKEN_V1,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenV2() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_NON_FUNGIBLE_TOKEN_V2,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenV3() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_NON_FUNGIBLE_TOKEN_V3,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenWithMetadata() {
        enableConfig();
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenWithCustomFeesV1() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenWithCustomFeesV2() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenWithCustomFeesV3() {
        attempt = prepareHtsAttemptWithSelector(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void matchesCreateNonFungibleTokenWithMetadataAndCustomFees() {
        enableConfig();
        attempt = prepareHtsAttemptWithSelectorAndCustomConfig(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                configuration);
        assertTrue(subject.matches(attempt));
    }

    @Test
    void falseOnInvalidSelector() {
        attempt = prepareHtsAttemptWithSelector(
                BURN_TOKEN_V2, subject, enhancement, addressIdConverter, verificationStrategies, gasCalculator);
        assertFalse(subject.matches(attempt));
    }

    @Test
    void callFromCreateTokenV1() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(5L));
        byte[] inputBytes =
                Bytes.wrapByteBuffer(CREATE_FUNGIBLE_TOKEN_V1.encodeCall(tuple)).toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateTokenV2() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                BigInteger.valueOf(10L),
                5L);
        byte[] inputBytes =
                Bytes.wrapByteBuffer(CREATE_FUNGIBLE_TOKEN_V2.encodeCall(tuple)).toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateTokenV3() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                10L,
                5);
        byte[] inputBytes =
                Bytes.wrapByteBuffer(CREATE_FUNGIBLE_TOKEN_V3.encodeCall(tuple)).toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateTokenWithMeta() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L),
                        "metadata".getBytes()),
                10L,
                5);
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_FUNGIBLE_TOKEN_WITH_METADATA.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateTokenWithCustomFeesV1() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                BigInteger.valueOf(10L),
                BigInteger.valueOf(5L),
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateTokenWithCustomFeesV2() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                BigInteger.valueOf(10L),
                5L,
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateTokenWithCustomFeesV3() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                10L,
                5,
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateTokenWithMetaAndCustomFees() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L),
                        "metadata".getBytes()),
                10L,
                5,
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftV1() {
        Tuple tuple = new Tuple(Tuple.of(
                "name",
                "symbol",
                NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                "memo",
                true,
                0L,
                false,
                new Tuple[] {},
                Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)));
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_NON_FUNGIBLE_TOKEN_V1.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftV2() {
        Tuple tuple = new Tuple(Tuple.of(
                "name",
                "symbol",
                NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                "memo",
                true,
                1000L,
                false,
                new Tuple[] {},
                Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)));
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_NON_FUNGIBLE_TOKEN_V2.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftV3() {
        Tuple tuple = new Tuple(Tuple.of(
                "name",
                "symbol",
                NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                "memo",
                true,
                1000L,
                false,
                new Tuple[] {},
                Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)));
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_NON_FUNGIBLE_TOKEN_V3.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftWithMeta() {
        Tuple tuple = new Tuple(Tuple.of(
                "name",
                "symbol",
                NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                "memo",
                true,
                1000L,
                false,
                new Tuple[] {},
                Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L),
                "metadata".getBytes()));
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftWithCustomFeesV1() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftWithCustomFeesV2() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftWithCustomFeesV3() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L)),
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);

        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    @Test
    void callFromCreateNftWithMetaAndCustomFees() {
        Tuple tuple = new Tuple(
                Tuple.of(
                        "name",
                        "symbol",
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        "memo",
                        true,
                        1000L,
                        false,
                        new Tuple[] {},
                        Tuple.of(0L, NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, 0L),
                        "metadata".getBytes()),
                EXPECTED_FIXED_CUSTOM_FEES.toArray(Tuple[]::new),
                new Tuple[] {});
        byte[] inputBytes = Bytes.wrapByteBuffer(
                        CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES.encodeCall(tuple))
                .toArray();
        given(attempt.inputBytes()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(attempt.defaultVerificationStrategy()).willReturn(verificationStrategy);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        assertThat(subject.callFrom(attempt)).isInstanceOf(ClassicCreatesCall.class);
    }

    private void enableConfig() {
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.metadataKeyAndFieldEnabled()).willReturn(true);
    }
}
