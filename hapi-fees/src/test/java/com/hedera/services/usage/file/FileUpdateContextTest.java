package com.hedera.services.usage.file;

import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.KeyList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUpdateContextTest {
	String memo = "Currently unavailable";
	long expiry = 1_234_567L;
	KeyList wacl = KeyUtils.A_KEY_LIST.getKeyList();
	long size = 54_321L;

	@Test
	void buildsAsExpected() {
		// given:
		var subject = FileUpdateContext.newBuilder()
				.setCurrentExpiry(expiry)
				.setCurrentMemo(memo)
				.setCurrentWacl(wacl)
				.setCurrentSize(size)
				.build();

		// expect:
		assertEquals(memo, subject.currentMemo());
		assertEquals(expiry, subject.currentExpiry());
		assertEquals(wacl, subject.currentWacl());
		assertEquals(size, subject.currentSize());
	}

	@Test
	void rejectsIncompleteContext() {
		// given:
		var builder = FileUpdateContext.newBuilder()
				.setCurrentExpiry(expiry)
				.setCurrentMemo(memo)
				.setCurrentWacl(wacl);

		// expect:
		assertThrows(IllegalStateException.class, builder::build);
	}
}