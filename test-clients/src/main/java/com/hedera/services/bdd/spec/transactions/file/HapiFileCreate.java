package com.hedera.services.bdd.spec.transactions.file;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiFileCreate extends HapiTxnOp<HapiFileCreate> {
	static final Logger log = LogManager.getLogger(HapiFileCreate.class);

	private Key waclKey;
	private final String fileName;
	private boolean immutable = false;
	OptionalLong lifetime = OptionalLong.empty();
	Optional<String> contentsPath = Optional.empty();
	Optional<byte[]> contents = Optional.empty();
	Optional<SigControl> waclControl = Optional.empty();
	Optional<String> keyName = Optional.empty();
	Optional<String> resourceName = Optional.empty();
	AtomicReference<Timestamp> expiryUsed = new AtomicReference<>();

	public HapiFileCreate(String fileName) {
		this.fileName = fileName;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.FileCreate;
	}

	@Override
	protected Key lookupKey(HapiApiSpec spec, String name) {
		return name.equals(fileName) ? waclKey : spec.registry().getKey(name);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		if (immutable) {
			return super.defaultSigners();
		} else {
			return Arrays.asList(
					spec -> spec.registry().getKey(effectivePayer(spec)),
					ignore -> waclKey);
		}
	}

	public HapiFileCreate unmodifiable() {
		immutable = true;
		return this;
	}

	public HapiFileCreate lifetime(long secs) {
		this.lifetime = OptionalLong.of(secs);
		return this;
	}

	public HapiFileCreate key(String keyName) {
		this.keyName = Optional.of(keyName);
		return this;
	}

	public HapiFileCreate waclShape(SigControl shape) {
		waclControl = Optional.of(shape);
		return this;
	}

	public HapiFileCreate contents(byte[] data) {
		contents = Optional.of(data);
		return this;
	}

	public HapiFileCreate contents(String s) {
		contents = Optional.of(s.getBytes());
		return this;
	}

	public HapiFileCreate fromResource(String name) {
		var baos = new ByteArrayOutputStream();
		try {
			HapiFileCreate.class.getClassLoader().getResourceAsStream(name).transferTo(baos);
			baos.close();
			contents = Optional.of(baos.toByteArray());
		} catch (IOException e) {
			log.warn(toString() + " failed to read bytes from resource '" + name + "'!", e);
			throw new IllegalArgumentException(e);
		}
		return this;
	}

	public HapiFileCreate path(String path) {
		try {
			contentsPath = Optional.of(path);
			contents = Optional.of(Files.toByteArray(new File(path)));
		} catch (Throwable t) {
			log.warn(toString() + " failed to read bytes from '" + path + "'!", t);
		}
		return this;
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		if (!immutable) {
			generateWaclKey(spec);
		}
		FileCreateTransactionBody opBody = spec
				.txns()
				.<FileCreateTransactionBody, FileCreateTransactionBody.Builder>body(
						FileCreateTransactionBody.class, builder -> {
							if (!immutable) {
								builder.setKeys(waclKey.getKeyList());
							}
							contents.ifPresent(b -> builder.setContents(ByteString.copyFrom(b)));
							lifetime.ifPresent(s -> builder.setExpirationTime(TxnFactory.expiryGiven(s)));
						});
		return b -> {
			expiryUsed.set(opBody.getExpirationTime());
			b.setFileCreate(opBody);
		};
	}

	private void generateWaclKey(HapiApiSpec spec) {
		KeyGenerator generator = effectiveKeyGen();

		if (keyName.isPresent()) {
			waclKey = spec.registry().getKey(keyName.get());
			return;
		}

		if (waclControl.isPresent()) {
			SigControl control = waclControl.get();
			Assert.assertTrue(
					"WACL must be a KeyList!",
					control.getNature() == SigControl.Nature.LIST);
			waclKey = spec.keys().generateSubjectTo(control, generator);
		} else {
			waclKey = spec.keys().generate(KeyFactory.KeyType.LIST, generator);
		}
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) throws Throwable {
		if (actualStatus != SUCCESS) {
			return;
		}
		if (!immutable) {
			spec.registry().saveKey(fileName, waclKey);
		}
		spec.registry().saveFileId(fileName, lastReceipt.getFileID());
		spec.registry().saveTimestamp(fileName, expiryUsed.get());
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getFileSvcStub(targetNodeFor(spec), useTls)::createFile;
	}

	@Override
	protected HapiFileCreate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.FileCreate, fileFees::getFileCreateTxFeeMatrices, txn, numPayerSigs);
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper();
		contentsPath.ifPresent(p -> helper.add("path", p));
		Optional.ofNullable(lastReceipt).ifPresent(receipt ->
				helper.add("created", receipt.getFileID().getFileNum()));
		return helper;
	}

	public long numOfCreatedFile() {
		return Optional
				.ofNullable(lastReceipt)
				.map(receipt -> receipt.getFileID().getFileNum())
				.orElse(-1L);
	}
}
