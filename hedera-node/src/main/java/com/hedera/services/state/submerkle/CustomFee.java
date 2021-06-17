package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.util.Objects;

public class CustomFee implements SelfSerializable {
	static final byte FIXED_CODE = (byte)(1 << 0);
	static final byte FRACTIONAL_CODE = (byte)(1 << 1);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xf65baa433940f137L;

	private FeeType feeType;
	private EntityId feeCollector;
	private FixedFeeSpec fixedFeeSpec;
	private FractionalFeeSpec fractionalFeeSpec;

	enum FeeType {
		FRACTIONAL_FEE, FIXED_FEE
	}

	public CustomFee() {
		/* For RuntimeConstructable */
	}

	private CustomFee(
			FeeType feeType,
			EntityId feeCollector,
			FixedFeeSpec fixedFeeSpec,
			FractionalFeeSpec fractionalFeeSpec
	) {
		this.feeType = feeType;
		this.feeCollector = feeCollector;
		this.fixedFeeSpec = fixedFeeSpec;
		this.fractionalFeeSpec = fractionalFeeSpec;
	}

	public static CustomFee fractionalFee(
			long numerator,
			long denominator,
			long minimumUnitsToCollect,
			long maximumUnitsToCollect,
			EntityId feeCollector
	) {
		Objects.requireNonNull(feeCollector);
		final var spec = new FractionalFeeSpec(numerator, denominator, minimumUnitsToCollect, maximumUnitsToCollect);
		return new CustomFee(FeeType.FRACTIONAL_FEE, feeCollector, null, spec);
	}

	public static CustomFee fixedFee(
			long unitsToCollect,
			EntityId tokenDenomination,
			EntityId feeCollector
	) {
		Objects.requireNonNull(feeCollector);
		final var spec = new FixedFeeSpec(unitsToCollect, tokenDenomination);
		return new CustomFee(FeeType.FIXED_FEE, feeCollector, spec, null);
	}

	public EntityId getFeeCollector() {
		return feeCollector;
	}

	public FeeType getFeeType() {
		return feeType;
	}

	public FixedFeeSpec getFixedFeeSpec() {
		return fixedFeeSpec;
	}

	public FractionalFeeSpec getFractionalFeeSpec() {
		return fractionalFeeSpec;
	}

	@Override
	public void deserialize(SerializableDataInputStream din, int version) throws IOException {
		var byteCode = din.readByte();
		if (byteCode == FIXED_CODE) {
			feeType = FeeType.FIXED_FEE;
			var unitsToCollect = din.readLong();
			EntityId denom = din.readSerializable(true, EntityId::new);
			fixedFeeSpec = new FixedFeeSpec(unitsToCollect, denom);
		} else {
			feeType = FeeType.FRACTIONAL_FEE;
			var numerator = din.readLong();
			var denominator = din.readLong();
			var minimumUnitsToCollect = din.readLong();
			var maximumUnitsToCollect = din.readLong();
			fractionalFeeSpec = new FractionalFeeSpec(
					numerator, denominator, minimumUnitsToCollect, maximumUnitsToCollect);
		}

		feeCollector = din.readSerializable(true, EntityId::new);
	}

	@Override
	public void serialize(SerializableDataOutputStream dos) throws IOException {
		if (feeType == FeeType.FIXED_FEE) {
			dos.writeByte(FIXED_CODE);
			dos.writeLong(fixedFeeSpec.getUnitsToCollect());
			dos.writeSerializable(fixedFeeSpec.tokenDenomination, true);
		} else {
			dos.writeByte(FRACTIONAL_CODE);
			dos.writeLong(fractionalFeeSpec.getNumerator());
			dos.writeLong(fractionalFeeSpec.getDenominator());
			dos.writeLong(fractionalFeeSpec.getMinimumUnitsToCollect());
			dos.writeLong(fractionalFeeSpec.getMaximumUnitsToCollect());
		}
		dos.writeSerializable(feeCollector, true);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	static class FractionalFeeSpec {
		private final long numerator;
		private final long denominator;
		private final long minimumUnitsToCollect;
		private final long maximumUnitsToCollect;

		public FractionalFeeSpec(
				long numerator,
				long denominator,
				long minimumUnitsToCollect,
				long maximumUnitsToCollect
		) {
			if (denominator == 0) {
				throw new IllegalArgumentException("Division by zero is not allowed");
			}
			this.numerator = numerator;
			this.denominator = denominator;
			this.minimumUnitsToCollect = minimumUnitsToCollect;
			this.maximumUnitsToCollect = maximumUnitsToCollect;
		}

		long getNumerator() {
			return numerator;
		}

		long getDenominator() {
			return denominator;
		}

		long getMinimumUnitsToCollect() {
			return minimumUnitsToCollect;
		}

		long getMaximumUnitsToCollect() {
			return maximumUnitsToCollect;
		}

		@Override
		public boolean equals(Object obj) {
			return EqualsBuilder.reflectionEquals(this, obj);
		}

		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(FractionalFeeSpec.class)
					.add("numerator", numerator)
					.add("denominator", denominator)
					.add("minimumUnitsToCollect", minimumUnitsToCollect)
					.add("maximumUnitsToCollect", maximumUnitsToCollect)
					.toString();
		}
	}

	static class FixedFeeSpec {
		private final long unitsToCollect;
		/* If null, fee is collected in ℏ */
		private final EntityId tokenDenomination;

		public FixedFeeSpec(long unitsToCollect, EntityId tokenDenomination) {
			this.unitsToCollect = unitsToCollect;
			this.tokenDenomination = tokenDenomination;
		}

		long getUnitsToCollect() {
			return unitsToCollect;
		}

		EntityId getTokenDenomination() {
			return tokenDenomination;
		}

		@Override
		public boolean equals(Object obj) {
			return EqualsBuilder.reflectionEquals(this, obj);
		}

		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(FixedFeeSpec.class)
					.add("unitsToCollect", unitsToCollect)
					.add("tokenDenomination", tokenDenomination)
					.toString();
		}
	}
}
