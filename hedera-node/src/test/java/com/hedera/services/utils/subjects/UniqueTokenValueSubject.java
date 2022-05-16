package com.hedera.services.utils.subjects;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.LongSubject;
import com.google.common.truth.PrimitiveByteArraySubject;
import com.google.common.truth.Subject;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.utils.NftNumPair;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

import static com.google.common.truth.Truth.assertAbout;

public class UniqueTokenValueSubject extends Subject {
	/** Truth extension hook for UniqueTokenValue to UniqueTokenValueSubject. */
	public static Factory<UniqueTokenValueSubject, UniqueTokenValue> uniqueTokenValues() {
		return UniqueTokenValueSubject::new;
	}

	/** Convenience function for mimicing assertThat for UniqueTokenValue. */
	public static UniqueTokenValueSubject assertThat(@Nullable UniqueTokenValue actual) {
		return assertAbout(uniqueTokenValues()).that(actual);
	}

	private final UniqueTokenValue actual;

	protected UniqueTokenValueSubject(final FailureMetadata metadata, final UniqueTokenValue actual) {
		super(metadata, actual);
		this.actual = actual;
	}

	public LongSubject owner() {
		return check("getOwner()").that(actual.getOwner().num());
	}

	public void hasOwner(final long expected) {
		owner().isEqualTo(expected);
	}

	public void hasOwner(final EntityId expected) {
		check("getOwner()").that(actual.getOwner()).isEqualTo(expected);
	}

	public LongSubject spender() {
		return check("getSpender()").that(actual.getSpender().num());
	}

	public void hasSpender(final long expected) {
		spender().isEqualTo(expected);
	}

	public void hasSpender(final EntityId expected) {
		check("getSpender()").that(actual.getSpender()).isEqualTo(expected);
	}

	public LongSubject packedCreationTime() {
		return check("getPackedCreationTime()").that(actual.getPackedCreationTime());
	}

	public void hasPackedCreationTime(final long expected) {
		packedCreationTime().isEqualTo(expected);
	}

	public void hasCreationTime(final RichInstant expected) {
		check("getCreationTime()").that(actual.getCreationTime()).isEqualTo(expected);
	}

	public void hasCreationTime(final Instant expected) {
		hasCreationTime(RichInstant.fromJava(expected));
	}

	public PrimitiveByteArraySubject metadata() {
		return check("getMetadata()").that(actual.getMetadata());
	}

	public void hasMetadata(byte[] expected) {
		metadata().isEqualTo(expected);
	}

	public void hasPrev(long tokenNum, long serialNum) {
		check("getPrev()").that(actual.getPrev()).isEqualTo(new NftNumPair(tokenNum, serialNum));
	}

	public void hasPrev(NftNumPair expected) {
		check("getPrev()").that(actual.getPrev()).isEqualTo(expected);
	}

	public void hasNext(long tokenNum, long serialNum) {
		check("getNext()").that(actual.getNext()).isEqualTo(new NftNumPair(tokenNum, serialNum));
	}

	public void hasNext(NftNumPair expected) {
		check("getNext()").that(actual.getNext()).isEqualTo(expected);
	}

	public void isImmutable() {
		check("isImmutable()").that(actual.isImmutable()).isTrue();
	}

	public void isNotImmutable() {
		check("isImmutable()").that(actual.isImmutable()).isFalse();
	}
}
