package com.hedera.services.legacy.core.jproto;

import com.hedera.services.legacy.exception.InvalidFileWACLException;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
class JFileInfoTest {
	@Test
	public void translatesDecoderException() throws InvalidFileWACLException {
		// given:
		var invalidKeyList = KeyList.newBuilder()
				.addKeys(Key.getDefaultInstance())
				.build();

		// when:
		assertThrows(InvalidFileWACLException.class, () -> JFileInfo.convertWacl(invalidKeyList));
	}
}