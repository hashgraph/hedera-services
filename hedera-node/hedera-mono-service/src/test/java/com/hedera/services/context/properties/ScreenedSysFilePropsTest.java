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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_FUNDING_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_TRANSFERS_MAX_LEN;
import static com.hedera.services.context.properties.PropertyNames.TOKENS_MAX_RELS_PER_INFO_QUERY;
import static com.hedera.services.context.properties.ScreenedSysFileProps.DEPRECATED_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.MISPLACED_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.UNPARSEABLE_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.UNTRANSFORMABLE_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.UNUSABLE_PROP_TPL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(LogCaptureExtension.class)
class ScreenedSysFilePropsTest {
    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private ScreenedSysFileProps subject;

    @BeforeEach
    void setup() {
        subject = new ScreenedSysFileProps();
    }

    @Test
    void delegationWorks() {
        subject.from121 = Map.of(TOKENS_MAX_RELS_PER_INFO_QUERY, 42);

        assertEquals(Set.of(TOKENS_MAX_RELS_PER_INFO_QUERY), subject.allPropertyNames());
        assertEquals(42, subject.getProperty(TOKENS_MAX_RELS_PER_INFO_QUERY));
        assertTrue(subject.containsProperty(TOKENS_MAX_RELS_PER_INFO_QUERY));
        assertFalse(subject.containsProperty("nonsense"));
    }

    @Test
    void ignoresNonGlobalDynamic() {
        subject.screenNew(withJust("notGlobalDynamic", "42"));

        assertTrue(subject.from121.isEmpty());
        assertThat(
                logCaptor.warnLogs(),
                contains(String.format(MISPLACED_PROP_TPL, "notGlobalDynamic")));
    }

    @Test
    void incorporatesStandardGlobalDynamic() {
        final var oldMap = subject.from121;

        subject.screenNew(
                withAllOf(
                        Map.of(
                                TOKENS_MAX_RELS_PER_INFO_QUERY, "42",
                                LEDGER_TRANSFERS_MAX_LEN, "42",
                                CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, "42")));

        assertEquals(42, subject.from121.get(TOKENS_MAX_RELS_PER_INFO_QUERY));
        assertEquals(42, subject.from121.get(LEDGER_TRANSFERS_MAX_LEN));
        assertEquals(42, subject.from121.get(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT));
        assertNotSame(oldMap, subject.from121);
    }

    @Test
    void incorporatesLegacyGlobalDynamicWithTransform() {
        subject.screenNew(withJust("defaultFeeCollectionAccount", "0.0.98"));

        assertEquals(1, subject.from121.size());
        assertEquals(98L, subject.from121.get(LEDGER_FUNDING_ACCOUNT));
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        String.format(
                                DEPRECATED_PROP_TPL,
                                "defaultFeeCollectionAccount",
                                LEDGER_FUNDING_ACCOUNT)));
    }

    @ParameterizedTest
    @CsvSource({
        "ABC, tokens.maxRelsPerInfoQuery, false, NumberFormatException",
        "CryptoCreate;CryptoTransfer;Oops, scheduling.whitelist, false, IllegalArgumentException",
        "CryptoCreate;CryptoTransfer;CryptoGetAccountBalance, scheduling.whitelist, true,",
        (MerkleToken.UPPER_BOUND_TOKEN_NAME_UTF8_BYTES + 1)
                + ", tokens.maxTokenNameUtf8Bytes, true,",
        "1, ledger.transfers.maxLen, true,",
        "1, ledger.tokenTransfers.maxLen, true,",
        (MerkleToken.UPPER_BOUND_SYMBOL_UTF8_BYTES + 1) + ", tokens.maxSymbolUtf8Bytes, true,",
        "-1, rates.intradayChangeLimitPercent, true,",
        "-1, contracts.maxRefundPercentOfGasLimit, true,",
        "101, contracts.maxRefundPercentOfGasLimit, true,",
    })
    void warnsOfUnusableOrUnparseable(
            String unsupported, final String prop, final boolean isUnusable, String exception) {
        unsupported = unsupported.replaceAll(";", ",");
        final var expectedLog =
                isUnusable
                        ? String.format(UNUSABLE_PROP_TPL, unsupported, prop)
                        : String.format(UNPARSEABLE_PROP_TPL, unsupported, prop, exception);

        subject.screenNew(withJust(prop, unsupported));

        assertTrue(subject.from121.isEmpty());
        assertThat(logCaptor.warnLogs(), contains(expectedLog));
    }

    @Test
    void warnsOfUntransformableGlobalDynamic() {
        subject.screenNew(withJust("defaultFeeCollectionAccount", "abc"));

        assertTrue(subject.from121.isEmpty());
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        "Property name 'defaultFeeCollectionAccount' is deprecated, please use"
                                + " 'ledger.fundingAccount' instead!",
                        String.format(
                                UNTRANSFORMABLE_PROP_TPL,
                                "abc",
                                "defaultFeeCollectionAccount",
                                "IllegalArgumentException"),
                        "Property 'defaultFeeCollectionAccount' is not global/dynamic, please find"
                                + " it a proper home!"));
    }

    private static ServicesConfigurationList withJust(final String name, final String value) {
        return ServicesConfigurationList.newBuilder().addNameValue(from(name, value)).build();
    }

    private static ServicesConfigurationList withAllOf(final Map<String, String> settings) {
        final var builder = ServicesConfigurationList.newBuilder();
        settings.forEach((key, value) -> builder.addNameValue(from(key, value)));
        return builder.build();
    }

    private static Setting from(final String name, final String value) {
        return Setting.newBuilder().setName(name).setValue(value).build();
    }
}
