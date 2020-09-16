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

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import org.junit.Assert;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.keys.SigControl.Nature.*;

public class SigControl implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Nature {SIG_ON, SIG_OFF, LIST, THRESHOLD}

	private final Nature nature;
	private int threshold = -1;
	private SigControl[] childControls = new SigControl[0];

	public static final SigControl ON = new SigControl(SIG_ON);
	public static final SigControl OFF = new SigControl(SIG_OFF);
	public static final SigControl ANY = new SigControl(SIG_ON);

	public Nature getNature() {
		return nature;
	}

	public int getThreshold() {
		return threshold;
	}

	public SigControl[] getChildControls() {
		return childControls;
	}

	public int numSimpleKeys() {
		return countSimpleKeys(this);
	}

	private int countSimpleKeys(SigControl controller) {
		return (EnumSet.of(SIG_ON, SIG_OFF).contains(controller.nature))
				? 1 : Stream.of(controller.childControls).mapToInt(this::countSimpleKeys).sum();
	}

	public boolean appliesTo(Key key) {
		if (this == ON || this == OFF) {
			return (!key.hasKeyList() && !key.hasThresholdKey());
		} else {
			KeyList composite = KeyFactory.getCompositeList(key);
			if (composite.getKeysCount() == childControls.length) {
				return IntStream
						.range(0, childControls.length)
						.allMatch(i -> childControls[i].appliesTo(composite.getKeys(i)));
			} else {
				return false;
			}
		}
	}

	public static SigControl listSigs(SigControl... childControls) {
		Assert.assertTrue("A list much have at least one child key!", childControls.length > 0);
		return new SigControl(childControls);
	}

	public static SigControl threshSigs(int M, SigControl... childControls) {
		Assert.assertTrue("A threshold much have at least one child key!", childControls.length > 0);
		return new SigControl(M, childControls);
	}

	protected SigControl(Nature nature) {
		this.nature = nature;
	}

	protected SigControl(SigControl... childControls) {
		nature = Nature.LIST;
		this.childControls = childControls;
	}

	protected SigControl(int threshold, SigControl... childControls) {
		nature = Nature.THRESHOLD;
		this.threshold = threshold;
		this.childControls = childControls;
	}

	@Override
	public String toString() {
		return "SigControl{" +
				"nature=" + nature +
				", threshold=" + threshold +
				", childControls=" + Arrays.toString(childControls) +
				'}';
	}
}
