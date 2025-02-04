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

package com.hedera.services.bdd.spec.transactions.contract;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.equivAccount;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.solidityIdFrom;
import static com.hedera.services.bdd.spec.transactions.contract.HapiContractCall.doGasLookup;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.encodeParametersForConstructor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

public class HapiContractCreate extends HapiBaseContractCreate<HapiContractCreate> {
    static final Key DEPRECATED_CID_ADMIN_KEY = Key.newBuilder()
            .setContractID(ContractID.newBuilder().setContractNum(1_234L))
            .build();

    public HapiContractCreate(String contract) {
        super(contract);
    }

    public HapiContractCreate(String contract, String abi, Object... args) {
        super(contract, abi, args);
    }

    public HapiContractCreate(
            @NonNull final String contract,
            @NonNull final BiConsumer<HapiSpec, ContractCreateTransactionBody.Builder> spec) {
        super(contract);
        this.spec = spec;
    }

    private Optional<String> autoRenewAccount = Optional.empty();
    private Optional<Integer> maxAutomaticTokenAssociations = Optional.empty();
    private Optional<ByteString> inlineInitcode = Optional.empty();
    private boolean convertableToEthCreate = true;

    @Nullable
    private BiConsumer<HapiSpec, ContractCreateTransactionBody.Builder> spec;

    public HapiContractCreate exposingNumTo(LongConsumer obs) {
        newNumObserver = Optional.of(obs);
        return this;
    }

    public HapiContractCreate maxAutomaticTokenAssociations(int max) {
        maxAutomaticTokenAssociations = Optional.of(max);
        return this;
    }

    public HapiContractCreate withExplicitParams(final Supplier<String> supplier) {
        explicitHexedParams = Optional.of(supplier);
        return this;
    }

    public HapiContractCreate proxy(String proxy) {
        this.proxy = Optional.of(proxy);
        return this;
    }

    public HapiContractCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiContractCreate inlineInitCode(final ByteString initCode) {
        inlineInitcode = Optional.of(initCode);
        return this;
    }

    @Override
    protected Key lookupKey(HapiSpec spec, String name) {
        return name.equals(contract) ? adminKey : spec.registry().getKey(name);
    }

    public HapiContractCreate exposingGasTo(ObjLongConsumer<ResponseCodeEnum> gasObserver) {
        this.gasObserver = Optional.of(gasObserver);
        return this;
    }

    public HapiContractCreate skipAccountRegistration() {
        shouldAlsoRegisterAsAccount = false;
        return this;
    }

    public HapiContractCreate uponSuccess(Consumer<HapiSpecRegistry> cb) {
        successCb = Optional.of(cb);
        return this;
    }

    public HapiContractCreate bytecode(String fileName) {
        bytecodeFile = Optional.of(fileName);
        return this;
    }

    public HapiContractCreate bytecode(Supplier<String> supplier) {
        bytecodeFileFn = Optional.of(supplier);
        return this;
    }

    public HapiContractCreate adminKey(KeyFactory.KeyType type) {
        adminKeyType = Optional.of(type);
        return this;
    }

    public HapiContractCreate adminKeyShape(SigControl controller) {
        adminKeyControl = Optional.of(controller);
        return this;
    }

    public HapiContractCreate autoRenewSecs(long period) {
        autoRenewPeriodSecs = Optional.of(period);
        return this;
    }

    public HapiContractCreate balance(long initial) {
        balance = Optional.of(initial);
        return this;
    }

    public HapiContractCreate gas(long amount) {
        gas = OptionalLong.of(amount);
        return this;
    }

    public HapiContractCreate entityMemo(String s) {
        memo = Optional.of(s);
        return this;
    }

    public HapiContractCreate omitAdminKey(final boolean omitAdminKey) {
        this.omitAdminKey = omitAdminKey;
        return this;
    }

    public HapiContractCreate omitAdminKey() {
        omitAdminKey = true;
        return this;
    }

    public HapiContractCreate immutable() {
        omitAdminKey = true;
        makeImmutable = true;
        return this;
    }

    public HapiContractCreate useDeprecatedAdminKey() {
        useDeprecatedAdminKey = true;
        return this;
    }

    public HapiContractCreate adminKey(String existingKey) {
        key = Optional.of(existingKey);
        return this;
    }

    public HapiContractCreate autoRenewAccountId(String id) {
        autoRenewAccount = Optional.of(id);
        return this;
    }

    public void args(Optional<Object[]> args) {
        this.args = args;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.ContractCreate;
    }

    @Override
    protected HapiContractCreate self() {
        return this;
    }

    public HapiContractCreate stakedAccountId(String idLit) {
        stakedAccountId = Optional.of(idLit);
        return this;
    }

    public HapiContractCreate stakedNodeId(long idLit) {
        stakedNodeId = Optional.of(idLit);
        return this;
    }

    public HapiContractCreate declinedReward(boolean isDeclined) {
        isDeclinedReward = isDeclined;
        return this;
    }

    public boolean isConvertableToEthCreate() {
        return convertableToEthCreate;
    }

    public HapiContractCreate refusingEthConversion() {
        convertableToEthCreate = false;
        return this;
    }

    @Override
    protected List<Function<HapiSpec, Key>> defaultSigners() {
        if (omitAdminKey || useDeprecatedAdminKey) {
            return super.defaultSigners();
        }
        List<Function<HapiSpec, Key>> signers =
                new ArrayList<>(List.of(spec -> spec.registry().getKey(effectivePayer(spec))));
        Optional.ofNullable(adminKey).ifPresent(k -> signers.add(ignore -> k));
        autoRenewAccount.ifPresent(id -> signers.add(spec -> spec.registry().getKey(id)));
        return signers;
    }

    @Override
    protected void updateStateOf(HapiSpec spec) throws Throwable {
        if (actualStatus != SUCCESS) {
            if (gasObserver.isPresent()) {
                doGasLookup(gas -> gasObserver.get().accept(actualStatus, gas), spec, txnSubmitted, true);
            }
            return;
        }
        final var newId = lastReceipt.getContractID();
        newNumObserver.ifPresent(obs -> obs.accept(newId.getContractNum()));
        if (shouldAlsoRegisterAsAccount) {
            spec.registry().saveAccountId(contract, equivAccount(lastReceipt.getContractID()));
        }
        spec.registry().saveKey(contract, (omitAdminKey || useDeprecatedAdminKey) ? MISSING_ADMIN_KEY : adminKey);
        spec.registry().saveContractId(contract, newId);
        spec.registry().saveContractInfo(contract, infoOfCreatedContractOrThrow());
        successCb.ifPresent(cb -> cb.accept(spec.registry()));
        if (advertiseCreation) {
            String banner = "\n\n"
                    + bannerWith(String.format(
                            "Created contract '%s' with id '0.0.%d'.",
                            contract, lastReceipt.getContractID().getContractNum()));
            log.info(banner);
        }
        if (gasObserver.isPresent()) {
            doGasLookup(gas -> gasObserver.get().accept(SUCCESS, gas), spec, txnSubmitted, true);
        }
    }

    /**
     * Returns the contract info of the created contract.
     *
     * @return the contract info
     */
    public ContractGetInfoResponse.ContractInfo infoOfCreatedContractOrThrow() {
        if (lastReceipt == null || memo.isEmpty() || autoRenewPeriodSecs.isEmpty()) {
            throw new IllegalStateException("Contract was not created");
        }
        final var builder = ContractGetInfoResponse.ContractInfo.newBuilder()
                .setContractAccountID(solidityIdFrom(lastReceipt.getContractID()))
                .setMemo(memo.get())
                .setAutoRenewPeriod(Duration.newBuilder()
                        .setSeconds(autoRenewPeriodSecs.get())
                        .build());
        if (!omitAdminKey && !useDeprecatedAdminKey) {
            builder.setAdminKey(adminKey);
        }
        return builder.build();
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        if (this.spec != null) {
            return b -> {
                try {
                    b.setContractCreateInstance(explicitContractCreate(spec));
                } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            };
        }
        if (!omitAdminKey && !useDeprecatedAdminKey) {
            generateAdminKey(spec);
        }
        if (inlineInitcode.isEmpty()) {
            if (bytecodeFileFn.isPresent()) {
                bytecodeFile = Optional.of(bytecodeFileFn.get().get());
            }
            if (!bytecodeFile.isPresent()) {
                setBytecodeToDefaultContract(spec);
            }
        }
        Optional<byte[]> params;
        if (explicitHexedParams.isPresent()) {
            params = explicitHexedParams.map(Supplier::get).map(CommonUtils::unhex);
        } else {
            params = abi.isPresent()
                    ? Optional.of(encodeParametersForConstructor(args.get(), abi.get()))
                    : Optional.empty();
        }
        if (memo.isEmpty()) {
            memo = Optional.of(spec.setup().defaultMemo());
        }
        if (autoRenewPeriodSecs.isEmpty()) {
            autoRenewPeriodSecs =
                    Optional.of(spec.setup().defaultAutoRenewPeriod().getSeconds());
        }
        ContractCreateTransactionBody opBody = spec.txns()
                .<ContractCreateTransactionBody, ContractCreateTransactionBody.Builder>body(
                        ContractCreateTransactionBody.class, b -> {
                            if (useDeprecatedAdminKey) {
                                b.setAdminKey(DEPRECATED_CID_ADMIN_KEY);
                            } else if (omitAdminKey) {
                                if (makeImmutable) {
                                    b.setAdminKey(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()));
                                }
                            } else {
                                b.setAdminKey(adminKey);
                            }
                            inlineInitcode.ifPresentOrElse(
                                    b::setInitcode, () -> b.setFileID(TxnUtils.asFileId(bytecodeFile.get(), spec)));
                            autoRenewPeriodSecs.ifPresent(p -> b.setAutoRenewPeriod(
                                    Duration.newBuilder().setSeconds(p).build()));
                            balance.ifPresent(b::setInitialBalance);
                            memo.ifPresent(b::setMemo);
                            gas.ifPresent(b::setGas);
                            proxy.ifPresent(p -> b.setProxyAccountID(asId(p, spec)));
                            params.ifPresent(bytes -> b.setConstructorParameters(ByteString.copyFrom(bytes)));
                            autoRenewAccount.ifPresent(p -> b.setAutoRenewAccountId(asId(p, spec)));
                            maxAutomaticTokenAssociations.ifPresent(b::setMaxAutomaticTokenAssociations);

                            if (stakedAccountId.isPresent()) {
                                b.setStakedAccountId(asId(stakedAccountId.get(), spec));
                            } else if (stakedNodeId.isPresent()) {
                                b.setStakedNodeId(stakedNodeId.get());
                            }
                            b.setDeclineReward(isDeclinedReward);
                        });
        return b -> b.setContractCreateInstance(opBody);
    }

    private ContractCreateTransactionBody explicitContractCreate(HapiSpec spec)
            throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        return spec.txns()
                .<ContractCreateTransactionBody, ContractCreateTransactionBody.Builder>body(
                        ContractCreateTransactionBody.class,
                        b -> Objects.requireNonNull(this.spec).accept(spec, b));
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
        return spec.fees()
                .forActivityBasedOp(
                        HederaFunctionality.ContractCreate, scFees::getContractCreateTxFeeMatrices, txn, numPayerSigs);
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper().add("contract", contract);
        bytecodeFile.ifPresent(f -> helper.add("bytecode", f));
        memo.ifPresent(m -> helper.add("memo", m));
        autoRenewPeriodSecs.ifPresent(p -> helper.add("autoRenewPeriod", p));
        adminKeyControl.ifPresent(c -> helper.add("customKeyShape", Boolean.TRUE));
        Optional.ofNullable(lastReceipt)
                .ifPresent(
                        receipt -> helper.add("created", receipt.getContractID().getContractNum()));
        autoRenewAccount.ifPresent(p -> helper.add("autoRenewAccount", p));
        return helper;
    }

    public long numOfCreatedContract() {
        return Optional.ofNullable(lastReceipt)
                .map(receipt -> receipt.getContractID().getContractNum())
                .orElse(-1L);
    }

    public long numOfCreatedContractOrThrow() {
        return Optional.ofNullable(lastReceipt)
                .map(receipt -> receipt.getContractID().getContractNum())
                .orElseThrow();
    }

    public boolean getDeferStatusResolution() {
        return deferStatusResolution;
    }

    public String getTxnName() {
        return txnName;
    }

    public Optional<String> getAbi() {
        return abi;
    }

    public Optional<Object[]> getArgs() {
        return args;
    }

    public String getContract() {
        return contract;
    }

    public Optional<Function<Transaction, Transaction>> getFiddler() {
        return fiddler;
    }

    public Optional<Long> getValidDurationSecs() {
        return validDurationSecs;
    }

    public Optional<String> getCustomTxnId() {
        return customTxnId;
    }

    public Optional<AccountID> getNode() {
        return node;
    }

    public OptionalDouble getUsdFee() {
        return usdFee;
    }

    public Optional<Integer> getRetryLimits() {
        return retryLimits;
    }

    public Optional<EnumSet<ResponseCodeEnum>> getPermissiblePrechecks() {
        return permissiblePrechecks;
    }

    public Optional<Long> getFee() {
        return fee;
    }
}
