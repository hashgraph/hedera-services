package com.hedera.services.files;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.hedera.services.files.TieredHederaFs.IllegalArgumentType.DELETED_FILE;
import static com.hedera.services.files.TieredHederaFs.IllegalArgumentType.FILE_WOULD_BE_EXPIRED;
import static com.hedera.services.files.TieredHederaFs.IllegalArgumentType.OVERSIZE_CONTENTS;
import static com.hedera.services.files.TieredHederaFs.IllegalArgumentType.UNKNOWN_FILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Comparator.comparingInt;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

/**
 * A {@link HederaFs} that stores the contents and metadata of its files in
 * separate injected {@link Map}s.
 */
@Singleton
public final class TieredHederaFs implements HederaFs {
	private static final Logger log = LogManager.getLogger(TieredHederaFs.class);

	private final EntityIdSource ids;
	private final Supplier<Instant> now;
	private final Map<FileID, byte[]> data;
	private final Map<FileID, HFileMeta> metadata;
	private final GlobalDynamicProperties properties;

	final List<FileUpdateInterceptor> updateInterceptors = new ArrayList<>();

	static final int BYTES_PER_KB = 1024;
	private final Supplier<MerkleDiskFs> diskFs;

	public enum IllegalArgumentType {
		DELETED_FILE(ResponseCodeEnum.FILE_DELETED),
		UNKNOWN_FILE(ResponseCodeEnum.INVALID_FILE_ID),
		FILE_WOULD_BE_EXPIRED(ResponseCodeEnum.INVALID_EXPIRATION_TIME),
		OVERSIZE_CONTENTS(ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED);

		private final ResponseCodeEnum suggestedStatus;

		IllegalArgumentType(final ResponseCodeEnum suggestedStatus) {
			this.suggestedStatus = suggestedStatus;
		}

		public ResponseCodeEnum suggestedStatus() {
			return suggestedStatus;
		}
	}

	@Inject
	public TieredHederaFs(
			final EntityIdSource ids,
			final GlobalDynamicProperties properties,
			final Supplier<Instant> now,
			final Map<FileID, byte[]> data,
			final Map<FileID, HFileMeta> metadata,
			final Supplier<MerkleDiskFs> diskFs
	) {
		this.ids = ids;
		this.now = now;
		this.data = data;
		this.metadata = metadata;
		this.properties = properties;
		this.diskFs = diskFs;
	}

	public Map<FileID, byte[]> getData() {
		return data;
	}

	public Map<FileID, HFileMeta> getMetadata() {
		return metadata;
	}

	public MerkleDiskFs diskFs() {
		return diskFs.get();
	}

	@Override
	public void register(final FileUpdateInterceptor updateInterceptor) {
		updateInterceptors.add(updateInterceptor);
	}

	@Override
	public int numRegisteredInterceptors() {
		return updateInterceptors.size();
	}

	@Override
	public FileID create(final byte[] contents, final HFileMeta attr, final AccountID sponsor) {
		assertValid(attr);
		assertWithinSizeLimits(contents);

		final var fid = ids.newFileId(sponsor);
		data.put(fid, contents);
		metadata.put(fid, attr);

		return fid;
	}

	@Override
	public boolean exists(final FileID id) {
		return metadata.containsKey(id);
	}

	@Override
	public byte[] cat(final FileID id) {
		assertUsable(id);
		if (isOnDisk(id)) {
			return diskFs.get().contentsOf(id);
		} else {
			return data.get(id);
		}
	}

	@Override
	public HFileMeta getattr(final FileID id) {
		assertExtant(id);

		return metadata.get(id);
	}

	@Override
	public UpdateResult sudoSetattr(final FileID id, final HFileMeta attr) {
		assertExtant(id);
		assertValid(attr);

		return uncheckedSetattr(id, attr);
	}

	@Override
	public UpdateResult setattr(final FileID id, final HFileMeta attr) {
		assertUsable(id);
		assertValid(attr);

		return uncheckedSetattr(id, attr);
	}

	@Override
	public UpdateResult overwrite(final FileID id, final byte[] newContents) {
		assertUsable(id);
		if (!isOnDisk(id)) {
			assertWithinSizeLimits(newContents);
		}

		return uncheckedUpdate(id, newContents);
	}

	@Override
	public UpdateResult append(final FileID id, final byte[] moreContents) {
		assertUsable(id);

		boolean isDiskBased = isOnDisk(id);
		final var contents = isDiskBased ? diskFs.get().contentsOf(id) : data.get(id);

		final var newContents = ArrayUtils.addAll(contents, moreContents);
		final var idStr = EntityIdUtils.readableId(id);
		log.debug(
				"Appending {} bytes to {} :: new file will have {} bytes.",
				moreContents.length,
				idStr,
				newContents.length);

		if (!isDiskBased) {
			assertWithinSizeLimits(newContents);
		}

		return uncheckedUpdate(id, newContents);
	}

	@Override
	public void delete(final FileID id) {
		validateUsable(id);

		final var verdict = judge(id, FileUpdateInterceptor::preDelete);
		if (verdict.getValue()) {
			final var attr = metadata.get(id);
			attr.setDeleted(true);
			metadata.put(id, attr);
			data.remove(id);
		}
		if (verdict.getKey() != SUCCESS) {
			throw new InvalidTransactionException(verdict.getKey());
		}
	}

	@Override
	public void rm(final FileID id) {
		assertExtant(id);

		metadata.remove(id);
		data.remove(id);
	}

	public static final class SimpleUpdateResult implements UpdateResult {
		private final boolean attrChanged;
		private final boolean fileReplaced;
		private final ResponseCodeEnum outcome;

		public SimpleUpdateResult(
				final boolean attrChanged,
				final boolean fileReplaced,
				final ResponseCodeEnum outcome
		) {
			this.attrChanged = attrChanged;
			this.fileReplaced = fileReplaced;
			this.outcome = outcome;
		}

		@Override
		public boolean fileReplaced() {
			return fileReplaced;
		}

		@Override
		public ResponseCodeEnum outcome() {
			return outcome;
		}

		@Override
		public boolean attrChanged() {
			return attrChanged;
		}
	}

	private boolean isOnDisk(final FileID fid) {
		return diskFs.get().contains(fid);
	}

	private UpdateResult uncheckedSetattr(final FileID id, final HFileMeta attr) {
		final var verdict = judge(id, (interceptor, ignore) -> interceptor.preAttrChange(id, attr));

		if (verdict.getValue()) {
			metadata.put(id, attr);
		}

		return new SimpleUpdateResult(verdict.getValue(), false, verdict.getKey());
	}

	private UpdateResult uncheckedUpdate(final FileID id, final byte[] newContents) {
		final var verdict = judge(id, (interceptor, ignore) -> interceptor.preUpdate(id, newContents));

		if (verdict.getValue()) {
			if (diskFs.get().contains(id)) {
				diskFs.get().put(id, newContents);
			} else {
				data.put(id, newContents);
			}
			interceptorsFor(id).forEach(interceptor -> interceptor.postUpdate(id, newContents));
		}
		return new SimpleUpdateResult(false, verdict.getValue(), verdict.getKey());
	}

	private Map.Entry<ResponseCodeEnum, Boolean> judge(
			final FileID id,
			final BiFunction<FileUpdateInterceptor, FileID, Map.Entry<ResponseCodeEnum, Boolean>> judgment
	) {
		var outcome = SUCCESS;
		var should = true;

		final var orderedInterceptors = interceptorsFor(id);
		for (final var interceptor : orderedInterceptors) {
			final var vote = judgment.apply(interceptor, id);
			outcome = firstUnsuccessful(outcome, vote.getKey());
			if (!vote.getValue()) {
				should = false;
				break;
			}
		}

		return new AbstractMap.SimpleEntry<>(outcome, should);
	}

	private List<FileUpdateInterceptor> interceptorsFor(final FileID id) {
		return updateInterceptors
				.stream()
				.filter(interceptor -> interceptor.priorityForCandidate(id).isPresent())
				.sorted(comparingInt(interceptor -> interceptor.priorityForCandidate(id).getAsInt()))
				.collect(toList());
	}

	public static ResponseCodeEnum firstUnsuccessful(final ResponseCodeEnum... outcomes) {
		return Arrays.stream(outcomes).filter(not(SUCCESS::equals)).findAny().orElse(SUCCESS);
	}

	private void assertExtant(final FileID id) {
		if (!metadata.containsKey(id)) {
			throwIllegal(UNKNOWN_FILE);
		}
	}

	private void assertUsable(final FileID id) {
		assertExtant(id);
		if (metadata.get(id).isDeleted()) {
			throwIllegal(DELETED_FILE);
		}
	}

	private void validateUsable(final FileID id) {
		validateExtant(id);
		if (metadata.get(id).isDeleted()) {
			throw new InvalidTransactionException(FILE_DELETED);
		}
	}

	private void validateExtant(final FileID id) {
		if (!metadata.containsKey(id)) {
			throw new InvalidTransactionException(INVALID_FILE_ID);
		}
	}

	private void assertWithinSizeLimits(final byte[] data) {
		if (data.length > properties.maxFileSizeKb() * BYTES_PER_KB) {
			throwIllegal(OVERSIZE_CONTENTS);
		}
	}

	private void assertValid(final HFileMeta attr) {
		if (attr.getExpiry() < now.get().getEpochSecond()) {
			throwIllegal(FILE_WOULD_BE_EXPIRED);
		}
	}

	private void throwIllegal(final IllegalArgumentType type) {
		throw new IllegalArgumentException(type.toString());
	}
}
