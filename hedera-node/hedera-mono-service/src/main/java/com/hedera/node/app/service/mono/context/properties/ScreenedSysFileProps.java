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
package com.hedera.node.app.service.mono.context.properties;

import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static java.util.Map.entry;

import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class ScreenedSysFileProps implements PropertySource {
    private static final Logger log = LogManager.getLogger(ScreenedSysFileProps.class);

    static final String UNUSABLE_PROP_TPL = "Value '%s' is unusable for '%s', being ignored!";
    static final String MISPLACED_PROP_TPL =
            "Property '%s' is not global/dynamic, please find it a proper home!";
    static final String DEPRECATED_PROP_TPL =
            "Property name '%s' is deprecated, please use '%s' instead!";
    static final String UNPARSEABLE_PROP_TPL =
            "Value '%s' is unparseable for '%s' (%s), being ignored!";
    static final String UNTRANSFORMABLE_PROP_TPL =
            "Value '%s' is untransformable for deprecated '%s' (%s), being " + "ignored!";

    private static final Map<String, String> STANDARDIZED_NAMES =
            Map.ofEntries(
                    entry("defaultContractDurationSec", PropertyNames.CONTRACTS_DEFAULT_LIFETIME),
                    entry("maxGasLimit", PropertyNames.CONTRACTS_MAX_GAS_PER_SEC),
                    entry("maxFileSize", PropertyNames.FILES_MAX_SIZE_KB),
                    entry("defaultFeeCollectionAccount", PropertyNames.LEDGER_FUNDING_ACCOUNT),
                    entry("txReceiptTTL", PropertyNames.CACHE_RECORDS_TTL),
                    entry("exchangeRateAllowedPercentage", PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT),
                    entry("accountBalanceExportPeriodMinutes", PropertyNames.BALANCES_EXPORT_PERIOD_SECS),
                    entry("accountBalanceExportEnabled", PropertyNames.BALANCES_EXPORT_ENABLED),
                    entry("nodeAccountBalanceValidity", PropertyNames.BALANCES_NODE_BALANCE_WARN_THRESHOLD),
                    entry("accountBalanceExportDir", PropertyNames.BALANCES_EXPORT_DIR_PATH),
                    entry("transferListSizeLimit", PropertyNames.LEDGER_TRANSFERS_MAX_LEN),
                    entry("txMaximumDuration", PropertyNames.HEDERA_TXN_MAX_VALID_DURATION),
                    entry("txMinimumDuration", PropertyNames.HEDERA_TXN_MIN_VALID_DURATION),
                    entry("txMinimumRemaining", PropertyNames.HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS),
                    entry("maximumAutoRenewDuration", PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION),
                    entry("minimumAutoRenewDuration", PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION),
                    entry("localCallEstReturnBytes", PropertyNames.CONTRACTS_LOCAL_CALL_EST_RET_BYTES));
    private static final Map<String, UnaryOperator<String>> STANDARDIZED_FORMATS =
            Map.ofEntries(
                    entry(
                            "defaultFeeCollectionAccount",
                            legacy -> "" + parseAccount(legacy).getAccountNum()),
                    entry(
                            "accountBalanceExportPeriodMinutes",
                            legacy -> "" + (60 * Integer.parseInt(legacy))));

    @SuppressWarnings("unchecked")
    private static final Map<String, Predicate<Object>> VALUE_SCREENS =
            Map.ofEntries(
                    entry(
                            PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT,
                            limitPercent -> (int) limitPercent > 0),
                    entry(
                            PropertyNames.SCHEDULING_WHITE_LIST,
                            whitelist ->
                                    ((Set<HederaFunctionality>) whitelist)
                                            .stream()
                                                    .noneMatch(
                                                            MiscUtils.QUERY_FUNCTIONS::contains)),
                    entry(
                            PropertyNames.TOKENS_MAX_SYMBOL_UTF8_BYTES,
                            maxUtf8Bytes ->
                                    (int) maxUtf8Bytes
                                            <= MerkleToken.UPPER_BOUND_SYMBOL_UTF8_BYTES),
                    entry(
                            PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES,
                            maxUtf8Bytes ->
                                    (int) maxUtf8Bytes
                                            <= MerkleToken.UPPER_BOUND_TOKEN_NAME_UTF8_BYTES),
                    entry(PropertyNames.LEDGER_TRANSFERS_MAX_LEN, maxLen -> (int) maxLen >= 2),
                    entry(PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN, maxLen -> (int) maxLen >= 2),
                    entry(
                            PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT,
                            maxRefundPercentage ->
                                    (int) maxRefundPercentage >= 0
                                            && (int) maxRefundPercentage <= 100));

    Map<String, Object> from121 = Collections.emptyMap();

    @Inject
    public ScreenedSysFileProps() {
        /* No-op */
    }

    void screenNew(final ServicesConfigurationList rawProps) {
        from121 =
                rawProps.getNameValueList().stream()
                        .map(this::withStandardizedName)
                        .filter(this::isValidGlobalDynamic)
                        .filter(this::hasParseableValue)
                        .filter(this::isUsableGlobalDynamic)
                        .collect(
                                Collectors.toMap(
                                        Setting::getName, this::asTypedValue, (a, b) -> b));
        final var msg =
                "Global/dynamic properties overridden in system file are:\n  "
                        + BootstrapProperties.GLOBAL_DYNAMIC_PROPS.stream()
                                .filter(from121::containsKey)
                                .sorted()
                                .map(name -> String.format("%s=%s", name, from121.get(name)))
                                .collect(Collectors.joining("\n  "));
        log.info(msg);
    }

    private boolean isUsableGlobalDynamic(final Setting prop) {
        final var name = prop.getName();
        return Optional.ofNullable(VALUE_SCREENS.get(name))
                .map(
                        screen -> {
                            final var usable = screen.test(asTypedValue(prop));
                            if (!usable) {
                                log.warn(String.format(UNUSABLE_PROP_TPL, prop.getValue(), name));
                            }
                            return usable;
                        })
                .orElse(true);
    }

    private boolean isValidGlobalDynamic(final Setting prop) {
        final var name = prop.getName();
        final var clearlyBelongs = BootstrapProperties.GLOBAL_DYNAMIC_PROPS.contains(name);
        if (!clearlyBelongs) {
            log.warn(String.format(MISPLACED_PROP_TPL, name));
        }
        return clearlyBelongs;
    }

    private Setting withStandardizedName(final Setting rawProp) {
        /* Note rawName is never null as gRPC object getters return a non-null default value for any missing field */
        final var rawName = rawProp.getName();
        final var standardizedName = STANDARDIZED_NAMES.getOrDefault(rawName, rawName);
        if (!rawName.equals(standardizedName)) {
            log.warn(String.format(DEPRECATED_PROP_TPL, rawName, standardizedName));
        }
        final var builder = rawProp.toBuilder().setName(standardizedName);
        if (STANDARDIZED_FORMATS.containsKey(rawName)) {
            try {
                builder.setValue(STANDARDIZED_FORMATS.get(rawName).apply(rawProp.getValue()));
            } catch (Exception reason) {
                log.warn(
                        String.format(
                                UNTRANSFORMABLE_PROP_TPL,
                                rawProp.getValue(),
                                rawName,
                                reason.getClass().getSimpleName()));
                return rawProp;
            }
        }
        return builder.build();
    }

    private Object asTypedValue(final Setting prop) {
        return BootstrapProperties.transformFor(prop.getName()).apply(prop.getValue());
    }

    private boolean hasParseableValue(final Setting prop) {
        try {
            BootstrapProperties.transformFor(prop.getName()).apply(prop.getValue());
            return true;
        } catch (Exception reason) {
            log.warn(
                    String.format(
                            UNPARSEABLE_PROP_TPL,
                            prop.getValue(),
                            prop.getName(),
                            reason.getClass().getSimpleName()));
            return false;
        }
    }

    @Override
    public boolean containsProperty(final String name) {
        return from121.containsKey(name);
    }

    @Override
    public Object getProperty(final String name) {
        return from121.get(name);
    }

    @Override
    public Set<String> allPropertyNames() {
        return from121.keySet();
    }
}
