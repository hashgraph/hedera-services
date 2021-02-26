package com.hedera.services.bdd.spec.transactions.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.equivAccount;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.solidityIdFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiContractCreate extends HapiTxnOp<HapiContractCreate> {
	static final Key MISSING_ADMIN_KEY = Key.getDefaultInstance();
	static final Key DEPRECATED_CID_ADMIN_KEY =
			Key.newBuilder().setContractID(ContractID.newBuilder().setContractNum(1_234L)).build();
	static final Logger log = LogManager.getLogger(HapiContractCreate.class);

	private Key adminKey;
	private boolean omitAdminKey = false;
	private boolean shouldAlsoRegisterAsAccount = true;
	private boolean useDeprecatedAdminKey = false;
	private final String contract;
	private OptionalLong gas = OptionalLong.empty();
	Optional<String> key = Optional.empty();
	Optional<Long> autoRenewPeriodSecs = Optional.empty();
	Optional<Long> balance = Optional.empty();
	Optional<SigControl> adminKeyControl = Optional.empty();
	Optional<KeyFactory.KeyType> adminKeyType = Optional.empty();
	Optional<String> memo = Optional.empty();
	Optional<String> bytecodeFile = Optional.empty();
	Optional<Supplier<String>> bytecodeFileFn = Optional.empty();
	Optional<Consumer<HapiSpecRegistry>> successCb = Optional.empty();
	Optional<String> abi = Optional.empty();
	Optional<Object[]> args = Optional.empty();

	public HapiContractCreate(String contract) {
		this.contract = contract;
	}

	public HapiContractCreate(String contract, String abi, Object... args) {
		this.contract = contract;
		this.abi = Optional.of(abi);
		this.args = Optional.of(args);
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractCreate;
	}

	@Override
	protected HapiContractCreate self() {
		return this;
	}

	@Override
	protected Key lookupKey(HapiApiSpec spec, String name) {
		return name.equals(contract) ? adminKey : spec.registry().getKey(name);
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

	public HapiContractCreate entityMemo(String s)	 {
		memo = Optional.of(s);
		return this;
	}
	public HapiContractCreate omitAdminKey()	 {
		omitAdminKey = true;
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

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return (omitAdminKey || useDeprecatedAdminKey)
				? super.defaultSigners()
				: List.of(spec -> spec.registry().getKey(effectivePayer(spec)), ignore -> adminKey);
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		if (shouldAlsoRegisterAsAccount) {
			spec.registry().saveAccountId(contract, equivAccount(lastReceipt.getContractID()));
		}
		spec.registry().saveKey(contract, (omitAdminKey || useDeprecatedAdminKey) ? MISSING_ADMIN_KEY : adminKey);
		spec.registry().saveContractId(contract, lastReceipt.getContractID());
		ContractGetInfoResponse.ContractInfo otherInfo = ContractGetInfoResponse.ContractInfo.newBuilder()
				.setContractAccountID(solidityIdFrom(lastReceipt.getContractID()))
				.setMemo(memo.orElse(spec.setup().defaultMemo()))
				.setAutoRenewPeriod(
						Duration.newBuilder().setSeconds(
								autoRenewPeriodSecs.orElse(spec.setup().defaultAutoRenewPeriod().getSeconds())).build())
				.build();
		spec.registry().saveContractInfo(contract, otherInfo);
		successCb.ifPresent(cb -> cb.accept(spec.registry()));
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		if (!omitAdminKey && !useDeprecatedAdminKey) {
			generateAdminKey(spec);
		}
		if (bytecodeFileFn.isPresent()) {
			bytecodeFile = Optional.of(bytecodeFileFn.get().get());
		}
		if (!bytecodeFile.isPresent()) {
			setBytecodeToDefaultContract(spec);
		}
		Optional<byte[]> params = abi.isPresent()
				? Optional.of(CallTransaction.Function.fromJsonInterface(abi.get()).encodeArguments(args.get()))
				: Optional.empty();
		FileID bytecodeFileId = TxnUtils.asFileId(bytecodeFile.get(), spec);
		ContractCreateTransactionBody opBody = spec
				.txns()
				.<ContractCreateTransactionBody, ContractCreateTransactionBody.Builder>body(
						ContractCreateTransactionBody.class, b -> {
							if (useDeprecatedAdminKey) {
								b.setAdminKey(DEPRECATED_CID_ADMIN_KEY);
							} else if (!omitAdminKey) {
								b.setAdminKey(adminKey);
							}
							b.setFileID(bytecodeFileId);
							autoRenewPeriodSecs.ifPresent(p ->
									b.setAutoRenewPeriod(Duration.newBuilder().setSeconds(p).build()));
							balance.ifPresent(a -> b.setInitialBalance(a));
							memo.ifPresent(m -> b.setMemo(m));
							gas.ifPresent(b::setGas);
							params.ifPresent(bytes -> b.setConstructorParameters(ByteString.copyFrom(bytes)));
							gas.ifPresent(a -> b.setGas(a));
						}
				);
		return b -> b.setContractCreateInstance(opBody);
	}

	private void generateAdminKey(HapiApiSpec spec) {
		if (key.isPresent()) {
			adminKey = spec.registry().getKey(key.get());
		} else {
			KeyGenerator generator = effectiveKeyGen();
			if (!adminKeyControl.isPresent()) {
				adminKey = spec.keys().generate(adminKeyType.orElse(KeyFactory.KeyType.SIMPLE), generator);
			} else {
				adminKey = spec.keys().generateSubjectTo(adminKeyControl.get(), generator);
			}
		}
	}

	private void setBytecodeToDefaultContract(HapiApiSpec spec) throws Throwable {
		String implicitBytecodeFile = contract + "Bytecode";
		HapiFileCreate fileCreate = TxnVerbs
				.fileCreate(implicitBytecodeFile)
				.path(spec.setup().defaultContractPath());
		Optional<Throwable> opError = fileCreate.execFor(spec);
		if (opError.isPresent()) {
			throw opError.get();
		}
		bytecodeFile = Optional.of(implicitBytecodeFile);
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.ContractCreate,
				scFees::getContractCreateTxFeeMatrices, txn, numPayerSigs);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::createContract;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("contract", contract);
		bytecodeFile.ifPresent(f -> helper.add("bytecode", f));
		memo.ifPresent(m -> helper.add("memo", m));
		autoRenewPeriodSecs.ifPresent(p -> helper.add("autoRenewPeriod", p));
		adminKeyControl.ifPresent(c -> helper.add("customKeyShape", Boolean.TRUE));
		Optional.ofNullable(lastReceipt)
				.ifPresent(receipt -> helper.add("created", receipt.getContractID().getContractNum()));
		return helper;
	}

	public long numOfCreatedContract() {
		return Optional
				.ofNullable(lastReceipt)
				.map(receipt -> receipt.getContractID().getContractNum())
				.orElse(-1L);
	}
}
