package com.hedera.services.utils;

import org.apache.tuweni.bytes.Bytes;

import java.util.Comparator;

/**
 * Compares Bytes as if they are overly large unsingned integers.
 */
public class BytesComparator implements Comparator<Bytes> {

	public static final BytesComparator INSTANCE = new BytesComparator();

	private BytesComparator() {
		// private to force singleton usage.
	}

	@Override
	public int compare(final Bytes b1, final Bytes b2) {
		final var result = nullCheck(b1, b2);
		if (result == 2) {
			return bytesCompare(b1, b2);
		}
		return result;
	}

	int nullCheck(Bytes b1, Bytes b2) {
		// null checks
		if (b1 == null) {
			return b2 == null ? 0 : 1;
		} else if (b2 == null) {
			return -1;
		}
		return 2;
	}

	int bytesCompare(Bytes b1, Bytes b2) {
		// size check - Longer is bigger
		int index = b1.size();
		int sizeCheck = Integer.compare(index, b2.size());
		if (sizeCheck != 0) {
			return sizeCheck;
		}

		// bytes check, big endian
		while (index > 0) {
			index--;
			int byteChcek = Integer.compare(b1.get(index), b2.get(index));
			if (byteChcek != 0) {
				return byteChcek;
			}
		}

		// must be equal
		return 0;
	}
}
