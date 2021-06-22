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
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import proto.CustomFeesOuterClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomFeesBalanceChangeTest {
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
		final var hbarRepr = "CustomFeesBalanceChange{token=ℏ, account=EntityId{shard=4, realm=5, num=6}, units=-1234}";
		final var tokenRepr = "CustomFeesBalanceChange{token=EntityId{shard=1, realm=2, num=3}, account=EntityId{shard=4, realm=5, num=6}, units=-1234}";

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
	void serializeWorksAsExpected() throws IOException {
		// setup:
		InOrder inOrder = Mockito.inOrder(dos);

		// given:
		final var subject = new CustomFeesBalanceChange(account, token, units);

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
		final var expectedBalanceChange = new CustomFeesBalanceChange(account, token, units);

		given(din.readSerializable())
				.willReturn(account)
				.willReturn(token);
		given(din.readLong()).willReturn(units);

		// given:
		final var subject = new CustomFeesBalanceChange();

		// when:
		subject.deserialize(din, CustomFeesBalanceChange.MERKLE_VERSION);

		// then:
		assertNotNull(subject);
		assertEquals(expectedBalanceChange.account(), subject.account());
		assertEquals(expectedBalanceChange.token(), subject.token());
		assertEquals(expectedBalanceChange.units(), subject.units());
	}

	@Test
	public void gettersWork() {
		//given
		final var  subject = new CustomFeesBalanceChange(account, token, units);
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
		final var subject = new CustomFeesBalanceChange(account, token, units);
		// then:
		CustomFeesOuterClass.CustomFeeCharged grpc = subject.toGrpc();

		//expect:
		assertEquals(subject.account().toGrpcAccountId(), grpc.getFeeCollector());
		assertEquals(subject.token().toGrpcTokenId(), grpc.getTokenId());
		assertEquals(subject.units(), grpc.getUnitsCharged());
	}

	@Test
	void testToGrpcForHbar(){
		// given:
		final var subject = new CustomFeesBalanceChange(account, units);
		// then:
		CustomFeesOuterClass.CustomFeeCharged grpc = subject.toGrpc();

		//expect:
		assertEquals(subject.account().toGrpcAccountId(), grpc.getFeeCollector());
		assertFalse(grpc.hasTokenId());
		assertEquals(subject.units(), grpc.getUnitsCharged());
	}

	@Test
	void testToGrpcWithBalanceChanges(){
		// given:
		final var subject = new CustomFeesBalanceChange(account, token, units);
		List<CustomFeesBalanceChange> balanceChanges = new ArrayList<>();
		balanceChanges.add(subject);

		// then:
		CustomFeesOuterClass.CustomFeesCharged grpc = subject.toGrpc(balanceChanges);

		//expect:
		assertEquals(1, grpc.getCustomFeesChargedCount());
		List<CustomFeesOuterClass.CustomFeeCharged> feesCharged = grpc.getCustomFeesChargedList();
		assertEquals(1, feesCharged.size());
		assertEquals(subject.account().toGrpcAccountId(), feesCharged.get(0).getFeeCollector());
		assertEquals(subject.token().toGrpcTokenId(), feesCharged.get(0).getTokenId());
		assertEquals(subject.units(), feesCharged.get(0).getUnitsCharged());
	}

	@Test
	void testFromGrpc(){
		// given:
		final var feeCharged = CustomFeesOuterClass.CustomFeeCharged
				.newBuilder()
				.setTokenId(token.toGrpcTokenId())
				.setFeeCollector(account.toGrpcAccountId())
				.setUnitsCharged(units)
				.build();
		final var grpc = CustomFeesOuterClass.CustomFeesCharged
				.newBuilder()
				.addCustomFeesCharged(feeCharged)
				.build();

		//expect:
		final var expectedBalanceChange = CustomFeesBalanceChange.fromGrpc(grpc);
		assertEquals(expectedBalanceChange.size(), 1);
		assertEquals(expectedBalanceChange.get(0).account(), account);
		assertEquals(expectedBalanceChange.get(0).token(), token);
		assertEquals(expectedBalanceChange.get(0).units(), units);
	}

	@Test
	void testFromGrpcForHbarAdjust(){
		// given:
		final var feeCharged = CustomFeesOuterClass.CustomFeeCharged
				.newBuilder()
				.setFeeCollector(account.toGrpcAccountId())
				.setUnitsCharged(units)
				.build();
		final var grpc = CustomFeesOuterClass.CustomFeesCharged
				.newBuilder()
				.addCustomFeesCharged(feeCharged)
				.build();

		//expect:
		final var expectedBalanceChange = CustomFeesBalanceChange.fromGrpc(grpc);
		assertEquals(expectedBalanceChange.size(), 1);
		assertEquals(expectedBalanceChange.get(0).account(), account);
		assertEquals(expectedBalanceChange.get(0).token(), null);
		assertEquals(expectedBalanceChange.get(0).units(), units);
	}

	@Test
	void merkleMethodsWork() {
		// given:
		final var subject = new CustomFeesBalanceChange();

		assertEquals(CustomFeesBalanceChange.MERKLE_VERSION, subject.getVersion());
		assertEquals(CustomFeesBalanceChange.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}
