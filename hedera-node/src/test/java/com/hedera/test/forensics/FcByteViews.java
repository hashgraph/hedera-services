package com.hedera.test.forensics;

/*-
 * ‌
 * Hedera Services Node
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FcByteViews {
	private final static int FCMAP_BEGIN = 1835364971;
	private final static int FCMAP_END = 1818588274;

	/* Accounts, Storage, Topics */
	private List<Integer> starts;
	private List<Integer> ends;

	private final byte[] signedState;

	public FcByteViews(byte[] signedState) {
		this.signedState = signedState;
		this.starts = offsetsOf(FCMAP_BEGIN, signedState);
		this.ends = offsetsOf(FCMAP_END, signedState);
	}

	public void vs(FcByteViews that) {
		System.out.println("Root hashes? " +
				Arrays.equals(
						this.signedState, 4, 52,
						that.signedState, 4, 52));

		System.out.println("BEGINNINGS :: " + this.starts + " -vs- " + that.starts
				+ " ? " + this.starts.equals(that.starts));
		System.out.println("ENDINGS    :: " + this.ends + " -vs- " + that.ends
				+ " ? " + this.ends.equals(that.ends));

		System.out.println("Accounts? " +
				Arrays.equals(
						this.signedState, this.starts.get(0), this.ends.get(0) + 4,
						that.signedState, that.starts.get(0), that.ends.get(0) + 4));
		System.out.println("Storage? " +
				Arrays.equals(
						this.signedState, this.starts.get(1), this.ends.get(1) + 4,
						that.signedState, that.starts.get(1), that.ends.get(1) + 4));
		System.out.println("Topics? " +
				Arrays.equals(
						this.signedState, this.starts.get(2), this.ends.get(2) + 4,
						that.signedState, that.starts.get(2), that.ends.get(2) + 4));

		System.out.println("Overall? " +
				Arrays.equals(
						this.signedState, 0, this.signedState.length,
						that.signedState, 0, that.signedState.length));
		if (this.signedState.length != that.signedState.length) {
			System.out.println("DIFFERENT LENGTHS");
		}
	}

	public static List<Integer> offsetsOf(int marker, byte[] signedState) {
		byte[] search = new byte[] {
				(byte) ((marker & (0xFF << 24)) >>> 24),
				(byte) ((marker & (0xFF << 16)) >>> 16),
				(byte) ((marker & (0xFF << 8)) >>> 8),
				(byte) (marker & 0xFF)
		};

		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i <= signedState.length - search.length; i++) {
			int j = 0;
			for (; j < search.length && search[j] == signedState[i + j]; j++) {
			}
			if (j == search.length) {
				offsets.add(i);
			}
		}
		return offsets;
	}
}
