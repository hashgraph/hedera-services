package com.hedera.services.bdd.spec.keys;

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

import org.junit.Assert;

import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class KeyShape extends SigControl {
	public static final KeyShape SIMPLE = new KeyShape(Nature.SIG_ON);

	protected KeyShape(SigControl.Nature nature) {
		super(nature);
	}

	protected KeyShape(SigControl... childControls) {
		super(childControls);
	}

	protected KeyShape(int threshold, SigControl... childControls) {
		super(threshold, childControls);
	}

	public static KeyShape listOf(int N) {
		return listOf(IntStream.range(0, N).mapToObj(ignore -> SIMPLE).toArray(n -> new KeyShape[n]));
	}

	public static KeyShape listOf(KeyShape... childShapes) {
		return new KeyShape(childShapes);
	}

	public static KeyShape threshOf(int M, int N) {
		Assert.assertTrue("A threshold key requires M <= N!", M <= N);
		return threshOf(M, IntStream.range(0, N).mapToObj(ignore -> SIMPLE).toArray(n -> new KeyShape[n]));
	}

	public static KeyShape threshOf(int M, KeyShape... childShapes) {
		return new KeyShape(M, childShapes);
	}

	public static KeyShape randomly(
			int depthAtMost,
			IntSupplier listSizeSupplier,
			Supplier<KeyFactory.KeyType> typeSupplier,
			Supplier<Map.Entry<Integer, Integer>> thresholdSizesSupplier
	) {
		KeyFactory.KeyType type = (depthAtMost == 1) ? KeyFactory.KeyType.SIMPLE : typeSupplier.get();
		switch (type) {
			case SIMPLE:
				return SIMPLE;
			case LIST:
				int listSize = listSizeSupplier.getAsInt();
				return listOf(randomlyListing(
						listSize,
						depthAtMost - 1,
						listSizeSupplier,
						typeSupplier,
						thresholdSizesSupplier));
			case THRESHOLD:
				Map.Entry<Integer, Integer> mOfN = thresholdSizesSupplier.get();
				int M = mOfN.getKey(), N = mOfN.getValue();
				return threshOf(M, randomlyListing(
						N,
						depthAtMost - 1,
						listSizeSupplier,
						typeSupplier,
						thresholdSizesSupplier));

		}
		throw new IllegalStateException("Unanticipated key type - " + type);
	}

	private static KeyShape[] randomlyListing(
			int N,
			int depthAtMost,
			IntSupplier listSizeSupplier,
			Supplier<KeyFactory.KeyType> typeSupplier,
			Supplier<Map.Entry<Integer, Integer>> thresholdSizesSupplier
	) {
		return IntStream
				.range(0, N)
				.mapToObj(ignore ->
						randomly(
								depthAtMost,
								listSizeSupplier,
								typeSupplier,
								thresholdSizesSupplier))
				.toArray(n -> new KeyShape[n]);
	}

	public static List<Object> sigs(Object... controls) {
		return List.of(controls);
	}

	public SigControl signedWith(Object control) {
		if (SIMPLE.getNature().equals(this.getNature())) {
			Assert.assertTrue(
					"Shape is simple but multiple controls given!",
					(control instanceof SigControl));
			return (SigControl) control;
		} else {
			KeyShape[] childShapes = (KeyShape[]) getChildControls();
			int size = childShapes.length;
			List<Object> controls = (List<Object>) control;
			Assert.assertEquals(
					"Shape is " + this.getNature() + "[n=" + size
							+ (this.getNature().equals(Nature.THRESHOLD) ? ",m=" + this.getThreshold() : "")
							+ "] but " + controls.size() + " controls given!",
					size, controls.size());
			SigControl[] childControls = IntStream
					.range(0, size)
					.mapToObj(i -> childShapes[i].signedWith(controls.get(i)))
					.toArray(n -> new SigControl[n]);
			if (this.getNature() == Nature.LIST) {
				return listSigs(childControls);
			} else {
				return threshSigs(this.getThreshold(), childControls);
			}
		}
	}
}
