/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.token;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.MoreObjects;
import com.google.protobuf.StringValue;
import com.hedera.node.app.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.node.app.hapi.fees.usage.token.TokenUpdateUsage;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.hip796.operations.TokenFeature;
import com.hedera.services.bdd.suites.utils.contracts.precompile.TokenKeyType;
import com.hederahashgraph.api.proto.java.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiTokenUpdate extends HapiTxnOp<HapiTokenUpdate> {
    static final Logger log = LogManager.getLogger(HapiTokenUpdate.class);

    private String token;

    private OptionalLong expiry = OptionalLong.empty();
    private OptionalLong autoRenewPeriod = OptionalLong.empty();
    private Optional<String> newMemo = Optional.empty();
    private Optional<String> newAdminKey = Optional.empty();
    private Optional<String> newKycKey = Optional.empty();
    private Optional<String> newWipeKey = Optional.empty();
    private Optional<String> newSupplyKey = Optional.empty();
    private Optional<String> newFreezeKey = Optional.empty();
    private Optional<String> newFeeScheduleKey = Optional.empty();
    private Optional<String> newPauseKey = Optional.empty();

    @Nullable
    private String newLockKey;

    @Nullable
    private String newPartitionKey;

    @Nullable
    private String newPartitionMoveKey;

    private Set<TokenFeature> rolesToRemove = EnumSet.noneOf(TokenFeature.class);
    private Optional<String> newSymbol = Optional.empty();
    private Optional<String> newName = Optional.empty();
    private Optional<String> newTreasury = Optional.empty();
    private Optional<String> autoRenewAccount = Optional.empty();
    private Optional<Supplier<Key>> newSupplyKeySupplier = Optional.empty();
    private Optional<Function<HapiSpec, String>> newSymbolFn = Optional.empty();
    private Optional<Function<HapiSpec, String>> newNameFn = Optional.empty();
    private boolean useEmptyAdminKeyList = false;
    private boolean useInvalidAdminKey = false;
    private boolean useInvalidWipeKey = false;
    private boolean useInvalidKycKey = false;
    private boolean useInvalidSupplyKey = false;
    private boolean useInvalidFreezeKey = false;
    private boolean useInvalidFeeScheduleKey = false;
    private boolean useInvalidPauseKey = false;
    private DeletedOrInvalidKeyTypes deletedOrInvalidKey = null;

    private enum DeletedOrInvalidKeyTypes {
        DELETED_ADMIN_KEY,
        INVALID_ADMIN_KEY,
        DELETED_WIPE_KEY,
        INVALID_WIPE_KEY,
        DELETED_KYC_KEY,
        INVALID_KYC_KEY,
        DELETED_SUPPLY_KEY,
        INVALID_SUPPLY_KEY,
        DELETED_FREEZE_KEY,
        INVALID_FREEZE_KEY,
        DELETED_FEE_SCHEDULE_KEY,
        INVALID_FEE_SCHEDULE_KEY,
        DELETED_PAUSE_KEY,
        INVALID_PAUSE_KEY,
    }

    private Optional<String> contractKeyName = Optional.empty();
    private Set<TokenKeyType> contractKeyAppliedTo = Set.of();

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenUpdate;
    }

    public HapiTokenUpdate(String token) {
        this.token = token;
    }

    public HapiTokenUpdate removingRoles(@NonNull final TokenFeature... rolesToRemove) {
        this.rolesToRemove = rolesToRemove.length == 0
                ? EnumSet.noneOf(TokenFeature.class)
                : Arrays.stream(rolesToRemove).collect(toCollection(() -> EnumSet.noneOf(TokenFeature.class)));
        return this;
    }

    public HapiTokenUpdate freezeKey(String name) {
        newFreezeKey = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate kycKey(String name) {
        newKycKey = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate wipeKey(String name) {
        newWipeKey = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate supplyKey(String name) {
        newSupplyKey = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate supplyKey(Supplier<Key> supplyKeySupplier) {
        newSupplyKeySupplier = Optional.of(supplyKeySupplier);
        return this;
    }

    public HapiTokenUpdate feeScheduleKey(String name) {
        newFeeScheduleKey = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate pauseKey(String name) {
        newPauseKey = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate lockKey(@NonNull final String name) {
        newLockKey = Objects.requireNonNull(name);
        return this;
    }

    public HapiTokenUpdate partitionKey(@NonNull final String name) {
        newPartitionKey = Objects.requireNonNull(name);
        return this;
    }

    public HapiTokenUpdate partitionMoveKey(@NonNull final String name) {
        newPartitionMoveKey = Objects.requireNonNull(name);
        return this;
    }

    public HapiTokenUpdate entityMemo(String memo) {
        this.newMemo = Optional.of(memo);
        return this;
    }

    public HapiTokenUpdate symbol(String symbol) {
        this.newSymbol = Optional.of(symbol);
        return this;
    }

    public HapiTokenUpdate symbol(Function<HapiSpec, String> symbolFn) {
        this.newSymbolFn = Optional.of(symbolFn);
        return this;
    }

    public HapiTokenUpdate name(String name) {
        this.newName = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate name(Function<HapiSpec, String> nameFn) {
        this.newNameFn = Optional.of(nameFn);
        return this;
    }

    public HapiTokenUpdate adminKey(String name) {
        newAdminKey = Optional.of(name);
        return this;
    }

    public HapiTokenUpdate treasury(String treasury) {
        this.newTreasury = Optional.of(treasury);
        return this;
    }

    public HapiTokenUpdate autoRenewAccount(String account) {
        this.autoRenewAccount = Optional.of(account);
        return this;
    }

    public HapiTokenUpdate autoRenewPeriod(long secs) {
        this.autoRenewPeriod = OptionalLong.of(secs);
        return this;
    }

    public HapiTokenUpdate expiry(long at) {
        this.expiry = OptionalLong.of(at);
        return this;
    }

    public HapiTokenUpdate properlyEmptyingAdminKey() {
        useEmptyAdminKeyList = true;
        return this;
    }

    public HapiTokenUpdate usingInvalidAdminKey() {
        useInvalidAdminKey = true;
        return this;
    }

    public HapiTokenUpdate usingInvalidWipeKey() {
        useInvalidWipeKey = true;
        return this;
    }

    public HapiTokenUpdate usingInvalidKycKey() {
        useInvalidKycKey = true;
        return this;
    }

    public HapiTokenUpdate usingInvalidSupplyKey() {
        useInvalidSupplyKey = true;
        return this;
    }

    public HapiTokenUpdate usingInvalidFreezeKey() {
        useInvalidFreezeKey = true;
        return this;
    }

    public HapiTokenUpdate usingInvalidFeeScheduleKey() {
        useInvalidFeeScheduleKey = true;
        return this;
    }

    public HapiTokenUpdate usingInvalidPauseKey() {
        useInvalidPauseKey = true;
        return this;
    }

    public HapiTokenUpdate contractKey(final Set<TokenKeyType> contractKeyAppliedTo, final String contractKeyName) {
        this.contractKeyName = Optional.of(contractKeyName);
        this.contractKeyAppliedTo = contractKeyAppliedTo;
        return this;
    }

    @Override
    protected HapiTokenUpdate self() {
        return this;
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        try {
            final TokenInfo info = HapiTokenFeeScheduleUpdate.lookupInfo(spec, token, log, loggingOff);
            FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> {
                var estimate =
                        TokenUpdateUsage.newEstimate(_txn, new TxnUsageEstimator(suFrom(svo), _txn, ESTIMATOR_UTILS));
                estimate.givenCurrentExpiry(info.getExpiry().getSeconds())
                        .givenCurrentMemo(info.getMemo())
                        .givenCurrentName(info.getName())
                        .givenCurrentSymbol(info.getSymbol());
                if (info.hasFreezeKey()) {
                    estimate.givenCurrentFreezeKey(Optional.of(info.getFreezeKey()));
                }
                if (info.hasAdminKey()) {
                    estimate.givenCurrentAdminKey(Optional.of(info.getAdminKey()));
                }
                if (info.hasSupplyKey()) {
                    estimate.givenCurrentSupplyKey(Optional.of(info.getSupplyKey()));
                }
                if (info.hasKycKey()) {
                    estimate.givenCurrentKycKey(Optional.of(info.getKycKey()));
                }
                if (info.hasWipeKey()) {
                    estimate.givenCurrentWipeKey(Optional.of(info.getWipeKey()));
                }
                if (info.hasFeeScheduleKey()) {
                    estimate.givenCurrentFeeScheduleKey(Optional.of(info.getFeeScheduleKey()));
                }
                if (info.hasPauseKey()) {
                    estimate.givenCurrentPauseKey(Optional.of(info.getPauseKey()));
                }
                if (info.hasAutoRenewAccount()) {
                    estimate.givenCurrentlyUsingAutoRenewAccount();
                }
                return estimate.get();
            };
            return spec.fees().forActivityBasedOp(HederaFunctionality.TokenUpdate, metricsCalc, txn, numPayerKeys);
        } catch (Throwable t) {
            log.warn("Couldn't estimate usage", t);
            return HapiSuite.ONE_HBAR;
        }
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        var id = TxnUtils.asTokenId(token, spec);
        if (newSymbolFn.isPresent()) {
            newSymbol = Optional.of(newSymbolFn.get().apply(spec));
        }
        if (newNameFn.isPresent()) {
            newName = Optional.of(newNameFn.get().apply(spec));
        }
        TokenUpdateTransactionBody opBody = spec.txns()
                .<TokenUpdateTransactionBody, TokenUpdateTransactionBody.Builder>body(
                        TokenUpdateTransactionBody.class, b -> {
                            b.setToken(id);
                            newSymbol.ifPresent(b::setSymbol);
                            newName.ifPresent(b::setName);
                            newMemo.ifPresent(s -> b.setMemo(
                                    StringValue.newBuilder().setValue(s).build()));
                            if (useInvalidAdminKey) {
                                b.setAdminKey(TxnUtils.ALL_ZEROS_INVALID_KEY);
                            } else if (useEmptyAdminKeyList) {
                                b.setAdminKey(TxnUtils.EMPTY_KEY_LIST);
                            } else {
                                newAdminKey.ifPresent(
                                        a -> b.setAdminKey(spec.registry().getKey(a)));
                            }
                            newTreasury.ifPresent(a -> b.setTreasury(asId(a, spec)));
                            if (useInvalidSupplyKey) {
                                b.setFreezeKey(TxnUtils.ALL_ZEROS_INVALID_KEY);
                            } else {
                                newSupplyKey.ifPresent(
                                        k -> b.setSupplyKey(spec.registry().getKey(k)));
                            }
                            newSupplyKeySupplier.ifPresent(s -> b.setSupplyKey(s.get()));
                            if (useInvalidWipeKey) {
                                b.setFreezeKey(TxnUtils.ALL_ZEROS_INVALID_KEY);
                            } else {
                                newWipeKey.ifPresent(
                                        k -> b.setWipeKey(spec.registry().getKey(k)));
                            }
                            if (useInvalidKycKey) {
                                b.setFreezeKey(TxnUtils.ALL_ZEROS_INVALID_KEY);
                            } else {
                                newKycKey.ifPresent(
                                        k -> b.setKycKey(spec.registry().getKey(k)));
                            }
                            if (useInvalidFeeScheduleKey) {
                                b.setFeeScheduleKey(TxnUtils.ALL_ZEROS_INVALID_KEY);
                            } else {
                                newFeeScheduleKey.ifPresent(
                                        k -> b.setFeeScheduleKey(spec.registry().getKey(k)));
                            }
                            if (useInvalidFreezeKey) {
                                b.setFreezeKey(TxnUtils.ALL_ZEROS_INVALID_KEY);
                            } else {
                                newFreezeKey.ifPresent(
                                        k -> b.setFreezeKey(spec.registry().getKey(k)));
                            }
                            if (useInvalidPauseKey) {
                                b.setFreezeKey(TxnUtils.ALL_ZEROS_INVALID_KEY);
                            } else {
                                newPauseKey.ifPresent(
                                        k -> b.setPauseKey(spec.registry().getKey(k)));
                            }
                            if (autoRenewAccount.isPresent()) {
                                var autoRenewId = TxnUtils.asId(autoRenewAccount.get(), spec);
                                b.setAutoRenewAccount(autoRenewId);
                            }
                            expiry.ifPresent(t -> b.setExpiry(
                                    Timestamp.newBuilder().setSeconds(t).build()));
                            autoRenewPeriod.ifPresent(secs -> b.setAutoRenewPeriod(
                                    Duration.newBuilder().setSeconds(secs).build()));
                            // We often want to use an existing contract to control the keys of various types (supply,
                            // freeze etc.)
                            // of a token, and in this case we need to use a Key{contractID=0.0.X} as the key; so for
                            // convenience we have a special case and allow the user to specify the name of the
                            // contract it should use from the registry to create this special key.
                            if (contractKeyName.isPresent() && !contractKeyAppliedTo.isEmpty()) {
                                final var contractId = spec.registry().getContractId(contractKeyName.get());
                                final var contractKey = Key.newBuilder()
                                        .setContractID(contractId)
                                        .build();
                                for (final var tokenKeyType : contractKeyAppliedTo) {
                                    switch (tokenKeyType) {
                                        case ADMIN_KEY -> b.setAdminKey(contractKey);
                                        case FREEZE_KEY -> b.setFreezeKey(contractKey);
                                        case KYC_KEY -> b.setKycKey(contractKey);
                                        case PAUSE_KEY -> b.setPauseKey(contractKey);
                                        case SUPPLY_KEY -> b.setSupplyKey(contractKey);
                                        case WIPE_KEY -> b.setWipeKey(contractKey);
                                        default -> throw new IllegalStateException(
                                                "Unexpected tokenKeyType: " + tokenKeyType);
                                    }
                                }
                            }
                        });
        return b -> b.setTokenUpdate(opBody);
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        List<Function<HapiSpec, Key>> signers = new ArrayList<>();
        signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));

        signers.add(spec -> {
            try {
                return spec.registry().getAdminKey(token);
            } catch (Exception ignore) {
                return Key.getDefaultInstance();
            }
        });
        newTreasury.ifPresent(n -> signers.add((spec -> spec.registry().getKey(n))));
        newAdminKey.ifPresent(n -> signers.add(spec -> spec.registry().getKey(n)));
        autoRenewAccount.ifPresent(a -> signers.add(spec -> spec.registry().getKey(a)));
        return signers;
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiSpec spec) {
        return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::updateToken;
    }

    @Override
    protected void updateStateOf(HapiSpec spec) {
        if (actualStatus != SUCCESS) {
            return;
        }
        var registry = spec.registry();
        if (useEmptyAdminKeyList) {
            registry.forgetAdminKey(token);
        }
        newMemo.ifPresent(m -> registry.saveMemo(token, m));
        newAdminKey.ifPresent(n -> registry.saveAdminKey(token, registry.getKey(n)));
        newSymbol.ifPresent(s -> registry.saveSymbol(token, s));
        newName.ifPresent(s -> registry.saveName(token, s));
        newFreezeKey.ifPresent(n -> registry.saveFreezeKey(token, registry.getKey(n)));
        newSupplyKey.ifPresent(n -> registry.saveSupplyKey(token, registry.getKey(n)));
        newWipeKey.ifPresent(n -> registry.saveWipeKey(token, registry.getKey(n)));
        newKycKey.ifPresent(n -> registry.saveKycKey(token, registry.getKey(n)));
        newFeeScheduleKey.ifPresent(n -> registry.saveFeeScheduleKey(token, registry.getKey(n)));
        newPauseKey.ifPresent(n -> registry.savePauseKey(token, registry.getKey(n)));
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper().add("token", token);
        return helper;
    }
}
