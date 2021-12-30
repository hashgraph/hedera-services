package com.hedera.services.sigs.order;

import com.google.protobuf.ByteString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LinkedRefs {
	private static final int EXPECTED_LINKED_NUMS = 1;

	private int i = 0;
	private Instant sourceSignedAt = Instant.EPOCH;
	private long[] linkedNums = new long[EXPECTED_LINKED_NUMS];
	private List<ByteString> linkedAliases = null;

	/**
	 * Records a given alias as linked to a transaction. Important when expanding signatures from
	 * a signed state, since during {@code handleTransaction()} we must re-expand signatures if any
	 * alias used during {@code expandSignatures()} has changed since the source state was signed.
	 *
	 * @param alias the alias to record as linked
	 */
	public void link(final ByteString alias) {
		if (linkedAliases == null) {
			linkedAliases = new ArrayList<>();
		}
		linkedAliases.add(alias);
	}

	/**
	 * Records a given entity num as linked to a transaction. Important when expanding signatures from
	 * a signed state, since during {@code handleTransaction()} we must re-expand signatures if any
	 * entity key used during {@code expandSignatures()} has changed since the source state was signed.
	 *
	 * @param num the entity number to record as linked
	 */
	public void link(final long num) {
		if (i == linkedNums.length) {
			linkedNums = Arrays.copyOf(linkedNums, 2 * linkedNums.length);
		}
		linkedNums[i++] = num;
	}

	/**
	 * Returns all entity numbers recorded as linked to a transaction in an array that may contain
	 * padding zeroes on the right. (Where zeroes should be ignored.)
	 *
	 * @return a possibly zero-padded array of the linked entity numbers
	 */
	public long[] linkedNumbers() {
		return linkedNums;
	}

	/**
	 * Returns all aliases recorded as linked to a transaction.
	 *
	 * @return the linked aliases
	 */
	public List<ByteString> linkedAliases() {
		return linkedAliases == null ? Collections.emptyList() : linkedAliases;
	}

	public Instant getSourceSignedAt() {
		return sourceSignedAt;
	}

	public void setSourceSignedAt(final Instant sourceSignedAt) {
		this.sourceSignedAt = sourceSignedAt;
	}
}
