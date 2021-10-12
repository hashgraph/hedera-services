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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Comparator.comparingInt;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

/**
 * A {@link HederaFs} that stores the contents and metadata of its files in
 * separate injected {@link Map}s.
 */
@Singleton
public class TieredHederaFs implements HederaFs {
	public static final Logger log = LogManager.getLogger(TieredHederaFs.class);

	private final EntityIdSource ids;
	private final Supplier<Instant> now;
	private final Map<FileID, byte[]> data;
	private final Map<FileID, HFileMeta> metadata;
	private final GlobalDynamicProperties properties;

	final List<FileUpdateInterceptor> updateInterceptors = new ArrayList<>();

	public static final int BYTES_PER_KB = 1024;
	private Supplier<MerkleDiskFs> diskFs;

	@Inject
	public TieredHederaFs(
			EntityIdSource ids,
			GlobalDynamicProperties properties,
			Supplier<Instant> now,
			Map<FileID, byte[]> data,
			Map<FileID, HFileMeta> metadata,
			Supplier<MerkleDiskFs> diskFs
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
	public void register(FileUpdateInterceptor updateInterceptor) {
		updateInterceptors.add(updateInterceptor);
	}

	@Override
	public int numRegisteredInterceptors() {
		return updateInterceptors.size();
	}

	@Override
	public FileID create(byte[] contents, HFileMeta attr, AccountID sponsor) {
		assertValid(attr);
		assertWithinSizeLimits(contents);

		var fid = ids.newFileId(sponsor);
		data.put(fid, contents);
		metadata.put(fid, attr);

		return fid;
	}

	@Override
	public boolean exists(FileID id) {
		return metadata.containsKey(id);
	}

	@Override
	public byte[] cat(FileID id) {
		assertUsable(id);
		if (isOnDisk(id)) {
			return diskFs.get().contentsOf(id);
		} else {
			return data.get(id);
		}
	}

	@Override
	public HFileMeta getattr(FileID id) {
		assertExtant(id);

		return metadata.get(id);
	}

	@Override
	public void sudoSetattr(FileID id, HFileMeta attr) {
		assertExtant(id);
		assertValid(attr);

		uncheckedSetattr(id, attr);
	}

	@Override
	public void setattr(FileID id, HFileMeta attr) {
		assertUsable(id);
		assertValid(attr);

		uncheckedSetattr(id, attr);
	}

	@Override
	public void overwrite(FileID id, byte[] newContents) {
		assertUsable(id);
		if (!isOnDisk(id)) {
			assertWithinSizeLimits(newContents);
		}

		uncheckedUpdate(id, newContents);
	}

	@Override
	public void append(FileID id, byte[] moreContents) {
		assertUsable(id);

		byte[] contents;

		boolean isDiskBased = isOnDisk(id);
		if (isDiskBased) {
			contents = diskFs.get().contentsOf(id);
		} else {
			contents = data.get(id);
		}
		var newContents = ArrayUtils.addAll(contents, moreContents);
		String idStr = EntityIdUtils.readableId(id);
		log.debug(
				"Appending {} bytes to {} :: new file will have {} bytes.",
				moreContents.length,
				idStr,
				newContents.length);

		if (!isDiskBased) {
			assertWithinSizeLimits(newContents);
		}

		uncheckedUpdate(id, newContents);
	}

	@Override
	public void delete(FileID id) {
		assertUsable(id);

		var deleted = judge(id, FileUpdateInterceptor::preDelete);
		if (deleted) {
			var attr = metadata.get(id);
			attr.setDeleted(true);
			metadata.put(id, attr);
			data.remove(id);
		}
	}

	@Override
	public void rm(FileID id) {
		assertExtant(id);

		metadata.remove(id);
		data.remove(id);
	}

	private boolean isOnDisk(FileID fid) {
		return diskFs.get().contains(fid);
	}

	private boolean uncheckedSetattr(FileID id, HFileMeta attr) {
		var changed = judge(id, (interceptor, ignore) -> interceptor.preAttrChange(id, attr));

		if (changed) {
			metadata.put(id, attr);
		}
		return changed;
	}

	private boolean uncheckedUpdate(FileID id, byte[] newContents) {
		var updated = judge(id, (interceptor, ignore) -> interceptor.preUpdate(id, newContents));

		if (updated) {
			if (diskFs.get().contains(id)) {
				diskFs.get().put(id, newContents);
			} else {
				data.put(id, newContents);
			}
			interceptorsFor(id).forEach(interceptor -> interceptor.postUpdate(id, newContents));
		}
		return updated;
	}

	private boolean judge(
			FileID id,
			BiFunction<FileUpdateInterceptor, FileID, Map.Entry<ResponseCodeEnum, Boolean>> judgment
	) {
		var orderedInterceptors = interceptorsFor(id);
		for (var interceptor : orderedInterceptors) {
			var vote = judgment.apply(interceptor, id);
			var outcome = firstUnsuccessful(SUCCESS, vote.getKey());
			validateTrue(outcome == SUCCESS, outcome);
			if (!vote.getValue()) {
				return false;
			}
		}
		return true;
	}

	private List<FileUpdateInterceptor> interceptorsFor(FileID id) {
		return updateInterceptors
				.stream()
				.filter(interceptor -> interceptor.priorityForCandidate(id).isPresent())
				.sorted(comparingInt(interceptor -> interceptor.priorityForCandidate(id).getAsInt()))
				.collect(toList());
	}

	public static ResponseCodeEnum firstUnsuccessful(ResponseCodeEnum... outcomes) {
		return Arrays.stream(outcomes).filter(not(SUCCESS::equals)).findAny().orElse(SUCCESS);
	}

	private void assertExtant(FileID id) {
		validateTrue(metadata.containsKey(id), INVALID_FILE_ID);
	}

	private void assertUsable(FileID id) {
		assertExtant(id);
		validateFalse(metadata.get(id).isDeleted(), FILE_DELETED);
	}

	private void assertWithinSizeLimits(byte[] data) {
		validateFalse(data.length > properties.maxFileSizeKb() * BYTES_PER_KB, MAX_FILE_SIZE_EXCEEDED);
	}

	private void assertValid(HFileMeta attr) {
		validateFalse(attr.getExpiry() < now.get().getEpochSecond(), INVALID_EXPIRATION_TIME);
	}
}
