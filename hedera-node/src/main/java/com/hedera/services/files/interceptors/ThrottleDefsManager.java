package com.hedera.services.files.interceptors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.throttling.ErrorCodeUtils;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.swirlds.common.AddressBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.throttling.ErrorCodeUtils.errorFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_THROTTLE_DEFINITIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;

public class ThrottleDefsManager implements FileUpdateInterceptor {
	private static final Logger log = LogManager.getLogger(ThrottleDefsManager.class);

	static final Map.Entry<ResponseCodeEnum, Boolean> YES_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(SUCCESS, true);
	static final Map.Entry<ResponseCodeEnum, Boolean> UNPARSEABLE_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(UNPARSEABLE_THROTTLE_DEFINITIONS, false);
	static final Map.Entry<ResponseCodeEnum, Boolean> DEFAULT_INVALID_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(INVALID_THROTTLE_DEFINITIONS, false);

	static final int APPLICABLE_PRIORITY = 0;

	private final FileNumbers fileNums;
	private final Supplier<AddressBook> addressBook;
	private final Consumer<ThrottleDefinitions> postUpdateCb;

	Function<ThrottleDefinitions, com.hedera.services.throttling.bootstrap.ThrottleDefinitions> toPojo =
			com.hedera.services.throttling.bootstrap.ThrottleDefinitions::fromProto;

	public ThrottleDefsManager(
			FileNumbers fileNums,
			Supplier<AddressBook> addressBook,
			Consumer<ThrottleDefinitions> postUpdateCb
	) {
		this.fileNums = fileNums;
		this.addressBook = addressBook;
		this.postUpdateCb = postUpdateCb;
	}

	@Override
	public OptionalInt priorityForCandidate(FileID id) {
		return isThrottlesDef(id) ? OptionalInt.of(APPLICABLE_PRIORITY) : OptionalInt.empty();
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preUpdate(FileID id, byte[] newContents) {
		Optional<ThrottleDefinitions> rates = uncheckedParseFrom(newContents);
		if (rates.isEmpty()) {
			return UNPARSEABLE_VERDICT;
		}

		var n = addressBook.get().getSize();
		var proto = rates.get();
		var defs = toPojo.apply(proto);
		for (var bucket : defs.getBuckets()) {
			try {
				bucket.asThrottleMapping(n);
			} catch (Exception e) {
				var detailError = errorFrom(e.getMessage());
				return detailError
						.<Map.Entry<ResponseCodeEnum, Boolean>>map(
								code -> new AbstractMap.SimpleImmutableEntry<>(code, false))
						.orElse(DEFAULT_INVALID_VERDICT);
			}
		}

		return YES_VERDICT;
	}

	@Override
	public void postUpdate(FileID id, byte[] contents) {
		/* Note - here we trust the file system to correctly invoke this interceptor
		only when we returned a priority from {@code priorityForCandidate}. */
		postUpdateCb.accept(uncheckedParseFrom(contents).get());
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preDelete(FileID id) {
		throw new UnsupportedOperationException("Cannot delete the throttle definitions file!");
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preAttrChange(FileID id, HFileMeta newAttr) {
		return YES_VERDICT;
	}

	private boolean isThrottlesDef(FileID fid) {
		return fid.getFileNum() == fileNums.throttleDefinitions();
	}

	private Optional<ThrottleDefinitions> uncheckedParseFrom(byte[] data) {
		try {
			return Optional.of(ThrottleDefinitions.parseFrom(data));
		} catch (InvalidProtocolBufferException ignore) {
			return Optional.empty();
		}
	}
}
