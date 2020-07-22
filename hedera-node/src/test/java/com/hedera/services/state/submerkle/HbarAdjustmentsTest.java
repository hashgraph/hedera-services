package com.hedera.services.state.submerkle;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.HbarAdjustments;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class HbarAdjustmentsTest {
	AccountID a = IdUtils.asAccount("0.0.13257");
	EntityId aEntity = EntityId.ofNullableAccountId(a);
	AccountID b = IdUtils.asAccount("0.0.13258");
	EntityId bEntity = EntityId.ofNullableAccountId(b);
	AccountID c = IdUtils.asAccount("0.0.13259");
	EntityId cEntity = EntityId.ofNullableAccountId(c);

	long aAmount = 1L, bAmount = 2L, cAmount = -3L;

	TransferList grpcAdjustments = TxnUtils.withAdjustments(a, aAmount, b, bAmount, c, cAmount);
	TransferList otherGrpcAdjustments = TxnUtils.withAdjustments(a, aAmount * 2, b, bAmount * 2, c, cAmount * 2);

	DataInputStream din;
	EntityId.Provider idProvider;

	HbarAdjustments subject;

	@BeforeEach
	public void setup() {
		din = mock(DataInputStream.class);
		idProvider = mock(EntityId.Provider.class);

		HbarAdjustments.legacyIdProvider = idProvider;

		subject = new HbarAdjustments();
		subject.accountIds = List.of(ofNullableAccountId(a), ofNullableAccountId(b), ofNullableAccountId(c));
		subject.hbars = new long[] { aAmount, bAmount, cAmount };
	}

	@AfterEach
	public void cleanup() {
		HbarAdjustments.legacyIdProvider = EntityId.LEGACY_PROVIDER;
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"HbarAdjustments{readable=" + "[0.0.13257 <- +1, 0.0.13258 <- +2, 0.0.13259 -> -3]" + "}",
				subject.toString());
	}

	@Test
	public void objectContractWorks() {
		// given:
		var one = subject;
		var two = HbarAdjustments.fromGrpc(otherGrpcAdjustments);
		var three = HbarAdjustments.fromGrpc(grpcAdjustments);

		// when:
		assertEquals(one, one);
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertEquals(one, three);
		assertNotEquals(one, two);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		given(din.readLong())
				.willReturn(-1L).willReturn(-2L)
				.willReturn(-1L).willReturn(-2L).willReturn(aAmount)
				.willReturn(-1L).willReturn(-2L).willReturn(bAmount)
				.willReturn(-1L).willReturn(-2L).willReturn(cAmount);
		given(din.readInt()).willReturn(3);
		given(idProvider.deserialize(din))
				.willReturn(aEntity)
				.willReturn(bEntity)
				.willReturn(cEntity);

		// when:
		var subjectRead = HbarAdjustments.LEGACY_PROVIDER.deserialize(din);

		// then:
		assertEquals(subject, subjectRead);
	}

	@Test
	public void viewWorks() {
		// expect:
		assertEquals(grpcAdjustments, subject.toGrpc());
	}

	@Test
	public void factoryWorks() {
		// expect:
		assertEquals(subject, HbarAdjustments.fromGrpc(grpcAdjustments));
	}

	@Test
	public void serializableDetWorks() {
		// expect;
		assertEquals(HbarAdjustments.MERKLE_VERSION, subject.getVersion());
		assertEquals(HbarAdjustments.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		given(in.readSerializableList(
						intThat(i -> i == HbarAdjustments.MAX_NUM_ADJUSTMENTS),
						booleanThat(Boolean.TRUE::equals),
						(Supplier<EntityId>)any())).willReturn(subject.accountIds);
		given(in.readLongArray(HbarAdjustments.MAX_NUM_ADJUSTMENTS)).willReturn(subject.hbars);

		// when:
		var readSubject = new HbarAdjustments();
		// and:
		readSubject.deserialize(in, HbarAdjustments.MERKLE_VERSION);

		// expect:
		assertEquals(readSubject, subject);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		ArgumentCaptor idsCaptor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor amountsCaptor = ArgumentCaptor.forClass(long[].class);
		// and:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.serialize(out);

		// then:
		verify(out).writeSerializableList(
				(List<EntityId>)idsCaptor.capture(),
				booleanThat(Boolean.TRUE::equals),
				booleanThat(Boolean.TRUE::equals));
		verify(out).writeLongArray((long[])amountsCaptor.capture());
		// and:
		assertArrayEquals(subject.hbars, (long[])amountsCaptor.getValue());
		assertEquals(subject.accountIds, idsCaptor.getValue());
	}
}