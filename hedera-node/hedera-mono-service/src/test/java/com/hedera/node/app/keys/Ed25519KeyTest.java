package com.hedera.node.app.keys;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ed25519KeyTest {
	private static final int ED25519_BYTE_LENGTH = 32;
	private Ed25519Key subject;

	@Test
	void nullInEd25519KeyConstructorIsNotAllowed() {
		assertThrows(NullPointerException.class, () -> new Ed25519Key((byte[]) null));
	}

	@Test
	void emptyEd25519KeyIsAllowed() {
		subject = new Ed25519Key(new byte[0]);
		assertTrue(subject.isEmpty());
		assertFalse(subject.isValid());
	}

	@Test
	void invalidEd25519KeyFails() {
		subject = new Ed25519Key(new byte[1]);
		assertFalse(subject.isEmpty());
		assertFalse(subject.isValid());

		subject = new Ed25519Key(new byte[ED25519_BYTE_LENGTH - 1]);
		assertFalse(subject.isEmpty());
		assertFalse(subject.isValid());
	}

	@Test
	void validEd25519KeyWorks() {
		subject = new Ed25519Key(new byte[ED25519_BYTE_LENGTH]);
		assertFalse(subject.isEmpty());
		assertTrue(subject.isValid());
	}
	@Test
	void testsIsPrimitive(){
		subject = new Ed25519Key(new byte[ED25519_BYTE_LENGTH]);
		assertTrue(subject.isPrimitive());
	}

	@Test
	void copyAndAsReadOnlyWorks(){
		subject = new Ed25519Key(new byte[ED25519_BYTE_LENGTH]);
		final var copy = subject.copy();
		assertEquals(subject, copy);
		assertNotSame(subject, copy);
	}

	@Test
	void equalsAndHashCodeWorks() {
		Ed25519Key key1 = new Ed25519Key("firstKey".getBytes());
		Ed25519Key key2 = new Ed25519Key("secondKey".getBytes());
		Ed25519Key key3 = new Ed25519Key("firstKey".getBytes());

		assertNotEquals(key1, key2);
		assertNotEquals(key1.hashCode(), key2.hashCode());
		assertEquals(key1, key3);
		assertEquals(key1.hashCode(), key3.hashCode());
		assertEquals(key1, key1);
		assertFalse(key1.equals(null));
	}

	@Test
	void toStringWorks(){
		subject = new Ed25519Key("firstKey".getBytes());
		final var expectedString = "Ed25519Key[key=66697273744b6579]";
		assertEquals(expectedString, subject.toString());
	}

	@Test
	void serializeWorks() throws IOException {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

		SerializableDataInputStream in = new SerializableDataInputStream(
				new ByteArrayInputStream(byteOut.toByteArray()));

		subject = new Ed25519Key("firstKey".getBytes());
		final var deserializedSubject = new Ed25519Key("nothing".getBytes());

		subject.serialize(out);
		deserializedSubject.deserialize(in, 1);

		assertEquals(subject, deserializedSubject);
	}
}
