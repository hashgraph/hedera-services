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
import com.hedera.services.bdd.spec.keys.deterministic.Bip0032;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.bdd.spec.HapiApiSpec;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class SpecKeyFromMnemonic extends UtilOp {
	static final Logger log = LogManager.getLogger(SpecKeyFromMnemonic.class);

	private final String name;
	private final String mnemonic;
	private Optional<String> linkedId = Optional.empty();

	public SpecKeyFromMnemonic(String name, String mnemonic) {
		this.name = name;
		this.mnemonic = mnemonic;
	}

	public SpecKeyFromMnemonic linkedTo(String id) {
		linkedId = Optional.of(id);
		return this;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		byte[] seed = Bip0032.seedFrom(mnemonic);
		byte[] privateKey = Bip0032.ed25519PrivateKeyFrom(seed);
		var params = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
		var privateKeySpec = new EdDSAPrivateKeySpec(privateKey, params);
		var pk = new EdDSAPrivateKey(privateKeySpec);
		var pubKeyHex = Hex.encodeHexString(pk.getAbyte());
		log.info("Hex-encoded public key: " + pubKeyHex);
		var key = populatedFrom(pk.getAbyte());
		spec.registry().saveKey(name, key);
		spec.keys().incorporate(name, pubKeyHex, pk, KeyShape.SIMPLE);
		linkedId.ifPresent(s -> spec.registry().saveAccountId(name, HapiPropertySource.asAccount(s)));
		return false;
	}

	private Key populatedFrom(byte[] pubKey) {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(pubKey))
				.build();
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		var helper = super.toStringHelper();
		helper.add("name", name);
		linkedId.ifPresent(s -> helper.add("linkedId", s));
		return helper;
	}
}
