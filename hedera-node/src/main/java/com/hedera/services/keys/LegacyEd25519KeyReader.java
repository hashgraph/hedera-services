package com.hedera.services.keys;

import com.hedera.services.legacy.core.AccountKeyListObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class LegacyEd25519KeyReader {
	public static final Logger log = LogManager.getLogger(LegacyEd25519KeyReader.class);

	public String hexedABytesFrom(String b64EncodedKeyPairLoc, String keyPairId) {
		try {
			var b64Encoded = Files.readString(Paths.get(b64EncodedKeyPairLoc));

			byte[] bytes = Base64.getDecoder().decode(b64Encoded);
			Map<String, List<AccountKeyListObj>> keyPairMap = (Map<String, List<AccountKeyListObj>>)readObjFrom(bytes);

			var keyPairs = keyPairMap.get(keyPairId);
			return keyPairs.get(0).getKeyPairList().get(0).getPublicKeyAbyteStr();
		} catch (Exception e) {
			log.warn("Unable to deserialize startup keystore!", e);
			throw new IllegalArgumentException(String.format(
					"No genesis key could be decrypted from '%s'!",
					b64EncodedKeyPairLoc));
		}
	}

	private Object readObjFrom(byte[] bytes) throws Exception {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
			try (ObjectInput oi = new ObjectInputStream(bais)) {
				return oi.readObject();
			}
		}
	}
}
