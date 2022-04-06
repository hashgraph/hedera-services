package com.hedera.services;

import com.hedera.test.utils.ClassLoaderHelper;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.SignedState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class ServicesStateE2ETest {
	private final String signedStateDir = "src/test/resources/signedState/";

	@BeforeAll
	public static void setUp() {
		ClassLoaderHelper.loadClassPathDependencies();
	}

	@Test
	@Disabled  // Flakey test -- re-enable after fixing.
	void testBasicState() throws IOException {
		loadSignedState(signedStateDir + "basicTest/SignedState.swh");
	}

	private static SignedState loadSignedState(String path) throws IOException {
		var signedPair = SignedStateFileManager.readSignedStateFromFile(new File(path));
		// Because it's possible we are loading old data, we cannot check equivalence of the hash.
		Assertions.assertNotNull(signedPair.getRight());
		return signedPair.getRight();
	}
}
