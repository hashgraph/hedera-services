package com.hedera.services.state.submerkle;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class SequenceNumberTest {
	long startNo = 123;
	SequenceNumber initSubject;
	SequenceNumber defaultSubject;

	@BeforeEach
	private void setup()  {
		initSubject = new SequenceNumber(startNo);
		defaultSubject = new SequenceNumber();
	}

	@Test
	public void incWorks() {
		// when:
		long seqNo = initSubject.getAndIncrement();

		// then:
		assertEquals(startNo, seqNo);
		// and:
		assertEquals(startNo + 1, initSubject.i);
		// and:
		assertEquals(startNo + 1, initSubject.current());
	}

	@Test
	public void copyWorks() {
		// when:
		var subjectCopy = initSubject.copy();

		// then:
		assertEquals(startNo, subjectCopy.i);
	}

	@Test
	public void serializesAsExpected() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		InOrder inOrder = inOrder(out);

		// when:
		initSubject.serialize(out);

		// then:
		inOrder.verify(out).writeLong(startNo);
	}

	@Test
	public void deserializesAsExpected() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		given(in.readLong()).willReturn(startNo);

		// when:
		defaultSubject.deserialize(in);

		// then:
		assertEquals(startNo, defaultSubject.i);
	}
}