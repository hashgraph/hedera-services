package com.hedera.services.store.models;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OwnershipTrackerTest {

	OwnershipTracker subject = new OwnershipTracker();
	final Id treasury = new Id(1, 2, 3);
	final Id account = new Id(4, 5, 6);
	final Id token = new Id(0, 0, 1);

	@BeforeEach
	void setup(){
		// setup the getter/setter test
		subject = new OwnershipTracker();
	}

	@Test
	void add() {
		OwnershipTracker.Change burn = OwnershipTracker.forRemoving(treasury, 1);
		subject.add(token, burn);
		assertEquals(subject.getChanges().size(), 1);
		subject.add(token, burn);
		assertEquals(subject.getChanges().size(), 1);
		assertFalse(subject.isEmpty());
	}

	@Test
	void fromMinting() {
		OwnershipTracker.Change change = OwnershipTracker.forMinting(treasury, 2);
		assertEquals(change.getNewOwner(), treasury);
		assertEquals(change.getPreviousOwner(), Id.DEFAULT);
		assertEquals(change.getSerialNumber(), 2);
	}

	@Test
	void fromWiping() {
		OwnershipTracker.Change change = OwnershipTracker.forRemoving(account, 2);
		assertEquals(change.getNewOwner(), Id.DEFAULT);
		assertEquals(change.getPreviousOwner(), account);
		assertEquals(change.getSerialNumber(), 2);
	}

	@Test
	void fromBurning() {
		OwnershipTracker.Change change = OwnershipTracker.forMinting(treasury, 2);
		assertEquals(change.getNewOwner(), treasury);
		assertEquals(change.getPreviousOwner(), Id.DEFAULT);
		assertEquals(change.getSerialNumber(), 2);
	}

	@Test
	void newChange(){
		OwnershipTracker.Change change = new OwnershipTracker.Change(treasury, Id.DEFAULT, 1);
		assertEquals(change.getSerialNumber(), 1);
		assertEquals(change.getNewOwner(), Id.DEFAULT);
		assertEquals(change.getPreviousOwner(), treasury);
	}
}