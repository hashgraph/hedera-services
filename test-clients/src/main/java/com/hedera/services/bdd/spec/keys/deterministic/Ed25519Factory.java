package com.hedera.services.bdd.spec.keys.deterministic;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hederahashgraph.api.proto.java.Key;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import org.bouncycastle.util.encoders.Hex;

public class Ed25519Factory {
	public static EdDSAPrivateKey ed25519From(byte[] privateKey) {
		var params = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
		var privateKeySpec = new EdDSAPrivateKeySpec(privateKey, params);
		return new EdDSAPrivateKey(privateKeySpec);
	}

	public static Key populatedFrom(byte[] pubKey) {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(pubKey))
				.build();
	}

	public static void main(String... args) {
		System.out.println(SpecKey.randomMnemonic());

//		String someWords = "<secret>";
//		var cryptoKey = SpecKey.asEd25519Key(someWords);
//		System.out.println(Hex.toHexString(cryptoKey.getAbyte()));
	}
}
