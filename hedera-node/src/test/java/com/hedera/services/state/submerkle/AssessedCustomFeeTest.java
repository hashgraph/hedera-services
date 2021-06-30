package com.hedera.services.state.submerkle;

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

import com.hedera.test.utils.IdUtils;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import proto.CustomFeesOuterClass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AssessedCustomFeeTest {
	private final EntityId account = new EntityId(4,5,6);
	private final long units = -1_234L;
	private final EntityId token = new EntityId(1, 2, 3);

	@Mock
	private SerializableDataInputStream din;
	@Mock
	private SerializableDataOutputStream dos;

	@Test
	void objectContractSanityChecks() {
		// given:
		final var hbarChange = IdUtils.hbarChangeForCustomFees(account.toGrpcAccountId(), units);
		final var tokenChange = IdUtils.tokenChangeForCustomFees(token, account.toGrpcAccountId(), units);
		// and:
		final var hbarRepr = "AssessedCustomFee{token=ℏ, account=EntityId{shard=4, realm=5, num=6}, units=-1234}";
		final var tokenRepr = "AssessedCustomFee{token=EntityId{shard=1, realm=2, num=3}, account=EntityId{shard=4, realm=5, num=6}, units=-1234}";

		// expect:
		assertNotEquals(hbarChange, tokenChange);
		assertNotEquals(hbarChange.hashCode(), tokenChange.hashCode());
		assertEquals(47129058, hbarChange.hashCode());
		assertEquals(48269287, tokenChange.hashCode());
		// and:
		assertEquals(hbarRepr, hbarChange.toString());
		assertEquals(tokenRepr, tokenChange.toString());
		// and:
		assertEquals(account, hbarChange.account());
		assertEquals(units, hbarChange.units());
		assertEquals(token, tokenChange.token());
	}

	@Test
	void liveFireSerdeWorksForHtsFee() throws IOException, ConstructableRegistryException {
		// setup:
		final var account = new EntityId(1, 2, 3);
		final var token = new EntityId(2, 3, 4);
		final var amount = 345L;
		final var subject = new AssessedCustomFee(account, token, amount);
		// and:
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		// and:
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);

		// given:
		subject.serialize(dos);
		dos.flush();
		// and:
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		// when:
		final var newSubject = new AssessedCustomFee();
		newSubject.deserialize(din, AssessedCustomFee.MERKLE_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void liveFireSerdeWorksForHbarFee() throws IOException, ConstructableRegistryException {
		// setup:
		final var account = new EntityId(1, 2, 3);
		final var amount = 345L;
		final var subject = new AssessedCustomFee(account, amount);
		// and:
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		// and:
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final var dos = new SerializableDataOutputStream(baos);

		// given:
		subject.serialize(dos);
		dos.flush();
		// and:
		final var bytes = baos.toByteArray();
		final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		final var din = new SerializableDataInputStream(bais);

		// when:
		final var newSubject = new AssessedCustomFee();
		newSubject.deserialize(din, AssessedCustomFee.MERKLE_VERSION);

		// then:
		assertEquals(subject, newSubject);
	}

	@Test
	void serializeWorksAsExpected() throws IOException {
		// setup:
		InOrder inOrder = Mockito.inOrder(dos);

		// given:
		final var subject = new AssessedCustomFee(account, token, units);

		// when:
		subject.serialize(dos);

		// then:
		inOrder.verify(dos).writeSerializable(account, true);
		inOrder.verify(dos).writeSerializable(token, true);
		inOrder.verify(dos).writeLong(units);
	}


	@Test
	void deserializeWorksAsExpected() throws IOException {
		// setup:
		final var expectedBalanceChange = new AssessedCustomFee(account, token, units);

		given(din.readSerializable())
				.willReturn(account)
				.willReturn(token);
		given(din.readLong()).willReturn(units);

		// given:
		final var subject = new AssessedCustomFee();

		// when:
		subject.deserialize(din, AssessedCustomFee.MERKLE_VERSION);

		// then:
		assertNotNull(subject);
		assertEquals(expectedBalanceChange.account(), subject.account());
		assertEquals(expectedBalanceChange.token(), subject.token());
		assertEquals(expectedBalanceChange.units(), subject.units());
	}

	@Test
	void gettersWork() {
		//given
		final var  subject = new AssessedCustomFee(account, token, units);
		// expect:
		assertEquals(account, subject.account());
		assertEquals(token, subject.token());
		assertEquals(units, subject.units());
	}


	@Test
	void recognizesIfForHbar() {
		// given:
		final var hbarChange = IdUtils.hbarChangeForCustomFees(account.toGrpcAccountId(), units);
		final var tokenChange = IdUtils.tokenChangeForCustomFees(token, account.toGrpcAccountId(), units);

		assertTrue(hbarChange.isForHbar());
		assertFalse(tokenChange.isForHbar());
	}

	@Test
	void testToGrpc(){
		// given:
		final var subject = new AssessedCustomFee(account, token, units);
		// then:
		CustomFeesOuterClass.AssessedCustomFee grpc = subject.toGrpc();

		//expect:
		assertEquals(subject.account().toGrpcAccountId(), grpc.getFeeCollectorAccountId());
		assertEquals(subject.token().toGrpcTokenId(), grpc.getTokenId());
		assertEquals(subject.units(), grpc.getAmount());
	}

	@Test
	void testToGrpcForHbar(){
		// given:
		final var subject = new AssessedCustomFee(account, units);
		// then:
		CustomFeesOuterClass.AssessedCustomFee grpc = subject.toGrpc();

		//expect:
		assertEquals(subject.account().toGrpcAccountId(), grpc.getFeeCollectorAccountId());
		assertFalse(grpc.hasTokenId());
		assertEquals(subject.units(), grpc.getAmount());
	}

	@Test
	void testFromGrpc(){
		// given:
		final var grpc = CustomFeesOuterClass.AssessedCustomFee
				.newBuilder()
				.setTokenId(token.toGrpcTokenId())
				.setFeeCollectorAccountId(account.toGrpcAccountId())
				.setAmount(units)
				.build();

		//expect:
		final var fcFee = AssessedCustomFee.fromGrpc(grpc);
		assertEquals(account, fcFee.account());
		assertEquals(token, fcFee.token());
		assertEquals(units, fcFee.units());
	}

	@Test
	void testFromGrpcForHbarAdjust(){
		// given:
		final var grpc = CustomFeesOuterClass.AssessedCustomFee
				.newBuilder()
				.setFeeCollectorAccountId(account.toGrpcAccountId())
				.setAmount(units)
				.build();

		//expect:
		final var fcFee = AssessedCustomFee.fromGrpc(grpc);
		assertEquals(account, fcFee.account());
		assertEquals(null, fcFee.token());
		assertEquals(units, fcFee.units());
	}

	@Test
	void merkleMethodsWork() {
		// given:
		final var subject = new AssessedCustomFee();

		assertEquals(AssessedCustomFee.MERKLE_VERSION, subject.getVersion());
		assertEquals(AssessedCustomFee.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}
