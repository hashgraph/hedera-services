package com.hedera.services.bdd.spec.utilops.inventory;

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
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hedera.services.bdd.suites.utils.keypairs.SpecUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.SigControl;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.File;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;

public class SpecKeyFromPem extends UtilOp {
	static final Logger log = LogManager.getLogger(SpecKeyFromPem.class);

	private static final String DEFAULT_PASSPHRASE = "swirlds";

	private static final SigControl SIMPLE = SigControl.ON;
	private static final SigControl SIMPLE_WACL = KeyShape.listOf(1);

	private final String pemLoc;
	private String passphrase = DEFAULT_PASSPHRASE;
	private SigControl control = SIMPLE;
	private Optional<String> name = Optional.empty();
	private Optional<String> linkedId = Optional.empty();

	public SpecKeyFromPem(String pemLoc) {
		this.pemLoc = pemLoc;
	}

	public SpecKeyFromPem simpleWacl() {
		this.control = SIMPLE_WACL;
		return this;
	}
	public SpecKeyFromPem passphrase(String secret) {
		passphrase = secret;
		return this;
	}
	public SpecKeyFromPem name(String custom) {
		name = Optional.of(custom);
		return this;
	}

	public SpecKeyFromPem linkedTo(String id) {
		linkedId = Optional.of(id);
		return this;
	}

	private String actualName() {
		return name.orElse(pemLoc.substring(0, pemLoc.indexOf(".pem")));
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		var ocKeystore = SpecUtils.asOcKeystore(new File(pemLoc), passphrase);
		var key = populatedFrom(ocKeystore);
		var real = actualName();
		linkedId.ifPresent(s -> spec.registry().saveAccountId(real, HapiPropertySource.asAccount(s)));
		spec.registry().saveKey(real, key);
		spec.keys().incorporate(real, ocKeystore, control);
		return false;
	}

	private Key populatedFrom(KeyPairObj ocKeystore) throws InvalidKeySpecException, DecoderException {
		if (control == SIMPLE) {
			return Key.newBuilder()
					.setEd25519(ByteString.copyFrom(Hex.decodeHex(ocKeystore.getPublicKeyAbyteStr())))
					.build();
		} else if (control == SIMPLE_WACL) {
			return Key.newBuilder()
					.setKeyList(KeyList.newBuilder()
							.addKeys(Key.newBuilder()
									.setEd25519(
											ByteString.copyFrom(Hex.decodeHex(ocKeystore.getPublicKeyAbyteStr())))))
					.build();
		} else {
			throw new IllegalStateException("Cannot populate key shape " + control);
		}
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		var helper = super.toStringHelper();
		helper.add("pem", pemLoc);
		name.ifPresent(n -> helper.add("name", n));
		return helper;
	}
}
