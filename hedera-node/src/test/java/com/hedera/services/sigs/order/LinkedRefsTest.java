package com.hedera.services.sigs.order;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LinkedRefsTest {
	private LinkedRefs subject = new LinkedRefs();

	@Test
	void canTrackAliases() {
		final var firstAlias = ByteString.copyFromUtf8("pretend");
		final var secondAlias = ByteString.copyFromUtf8("imaginary");

		assertSame(Collections.emptyList(), subject.linkedAliases());

		subject.link(firstAlias);
		subject.link(secondAlias);

		assertEquals(List.of(firstAlias, secondAlias), subject.linkedAliases());
	}

	@Test
	void canTrackNumbers() {
		for (long i = 1; i <= 10; i++) {
			subject.link(i);
		}

		assertArrayEquals(
				new long[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 },
				Arrays.copyOfRange(subject.linkedNumbers(), 0, 10));
	}

	@Test
	void canManageSourceSigningTime() {
		final var when = Instant.ofEpochSecond(1_234_567L);

		subject.setSourceSignedAt(when);
		assertSame(when, subject.getSourceSignedAt());
	}
}