package com.hedera.services.files;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.hedera.services.files.TieredHederaFs.IllegalArgumentType.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Comparator.comparingInt;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

/**
 * A {@link HederaFs} that stores the contents and metadata of its files in
 * separate injected {@link Map}s.
 */
public class TieredHederaFs implements HederaFs {
	public static final Logger log = LogManager.getLogger(TieredHederaFs.class);

	private final EntityIdSource ids;
	private final Supplier<Instant> now;
	private final Map<FileID, byte[]> data;
	private final Map<FileID, JFileInfo> metadata;
	private final GlobalDynamicProperties properties;

	final List<FileUpdateInterceptor> updateInterceptors = new ArrayList<>();

	public static final int BYTES_PER_KB = 1024;
	private SpecialFileSystem specialFileSystem;
	public enum IllegalArgumentType {
		DELETED_FILE(ResponseCodeEnum.FILE_DELETED),
		UNKNOWN_FILE(ResponseCodeEnum.INVALID_FILE_ID),
		FILE_WOULD_BE_EXPIRED(ResponseCodeEnum.INVALID_EXPIRATION_TIME),
		OVERSIZE_CONTENTS(ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED);

		private final ResponseCodeEnum suggestedStatus;

		IllegalArgumentType(ResponseCodeEnum suggestedStatus) {
			this.suggestedStatus = suggestedStatus;
		}

		public ResponseCodeEnum suggestedStatus() {
			return suggestedStatus;
		}
	}

	public TieredHederaFs(
			EntityIdSource ids,
			GlobalDynamicProperties properties,
			Supplier<Instant> now,
			Map<FileID, byte[]> data,
			Map<FileID, JFileInfo> metadata,
			SpecialFileSystem specialFileSystem
	) {
		this.ids = ids;
		this.now = now;
		this.data = data;
		this.metadata = metadata;
		this.properties = properties;
		this.specialFileSystem = specialFileSystem;
	}

	public Map<FileID, byte[]> getData() {
		return data;
	}

	public Map<FileID, JFileInfo> getMetadata() {
		return metadata;
	}

	@Override
	public void register(FileUpdateInterceptor updateInterceptor) {
		updateInterceptors.add(updateInterceptor);
	}

	@Override
	public FileID create(byte[] contents, JFileInfo attr, AccountID sponsor) {
		assertValid(attr);
		assertValid(contents);

		var fid = ids.newFileId(sponsor);
		data.put(fid, contents);

		return fid;
	}

	@Override
	public boolean exists(FileID id) {
		return metadata.containsKey(id);
	}

	@Override
	public byte[] cat(FileID id) {
		assertUsable(id);
		if (specialFileSystem.isSpeicalFileID(id)) {
			return specialFileSystem.getFileContent(id);
		} else {
			return data.get(id);
		}
	}

	@Override
	public JFileInfo getattr(FileID id) {
		assertExtant(id);

		return metadata.get(id);
	}

	@Override
	public UpdateResult sudoSetattr(FileID id, JFileInfo attr) {
		assertExtant(id);
		assertValid(attr);

		return uncheckedSetattr(id, attr);
	}

	@Override
	public UpdateResult setattr(FileID id, JFileInfo attr) {
		assertUsable(id);
		assertValid(attr);

		return uncheckedSetattr(id, attr);
	}

	@Override
	public UpdateResult overwrite(FileID id, byte[] newContents) {
		assertUsable(id);
		assertValid(newContents);

		return uncheckedUpdate(id, newContents);
	}

	@Override
	public UpdateResult append(FileID id, byte[] moreContents) {
		assertUsable(id);
		byte[] contents;
		if (specialFileSystem.isSpeicalFileID(id)) {
			contents = specialFileSystem.getFileContent(id);
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

		assertValid(newContents);

		return uncheckedUpdate(id, newContents);
	}

	@Override
	public UpdateResult delete(FileID id) {
		assertUsable(id);

		var verdict = judge(id, FileUpdateInterceptor::preDelete);
		if (verdict.getValue()) {
			var attr = metadata.get(id);
			attr.setDeleted(true);
			metadata.put(id, attr);
			data.remove(id);
		}
		return new SimpleUpdateResult(verdict.getValue(), verdict.getValue(), verdict.getKey());
	}

	@Override
	public void rm(FileID id) {
		assertExtant(id);

		metadata.remove(id);
		data.remove(id);
	}

	public static class SimpleUpdateResult implements UpdateResult {
		private final boolean attrChanged;
		private final boolean fileReplaced;
		private final ResponseCodeEnum outcome;

		public SimpleUpdateResult(
				boolean attrChanged,
				boolean fileReplaced,
				ResponseCodeEnum outcome
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

	private UpdateResult uncheckedSetattr(FileID id, JFileInfo attr) {
		var verdict = judge(id, (interceptor, ignore) -> interceptor.preAttrChange(id, attr));

		if (verdict.getValue()) {
			metadata.put(id, attr);
		}

		return new SimpleUpdateResult(verdict.getValue(), false, verdict.getKey());
	}


	private UpdateResult uncheckedUpdate(FileID id, byte[] newContents) {
		var verdict = judge(id, (interceptor, ignore) -> interceptor.preUpdate(id, newContents));

		if (verdict.getValue()) {
			if (specialFileSystem.isSpeicalFileID(id)) {
				specialFileSystem.put(id, newContents);
			} else {
				data.put(id, newContents);
			}
			interceptorsFor(id).forEach(interceptor -> interceptor.postUpdate(id, newContents));
		}
		return new SimpleUpdateResult(false, verdict.getValue(), verdict.getKey());
	}

	private Map.Entry<ResponseCodeEnum, Boolean> judge(
			FileID id,
			BiFunction<FileUpdateInterceptor, FileID, Map.Entry<ResponseCodeEnum, Boolean>> judgment
	) {
		var outcome = SUCCESS;
		var should = true;

		var orderedInterceptors = interceptorsFor(id);
		for (var interceptor : orderedInterceptors) {
			var vote = judgment.apply(interceptor, id);
			outcome = firstUnsuccessful(outcome, vote.getKey());
			if (!vote.getValue()) {
				should = false;
				break;
			}
		}

		return new AbstractMap.SimpleEntry<>(outcome, should);
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
		if (!metadata.containsKey(id)) {
			throwIllegal(UNKNOWN_FILE);
		}
	}

	private void assertUsable(FileID id) {
		assertExtant(id);
		if (metadata.get(id).isDeleted()) {
			throwIllegal(DELETED_FILE);
		}
	}

	private void assertValid(byte[] data) {
		if (data.length > properties.maxFileSizeKb() * BYTES_PER_KB) {
			throwIllegal(OVERSIZE_CONTENTS);
		}
	}

	private void assertValid(JFileInfo attr) {
		if (attr.getExpirationTimeSeconds() < now.get().getEpochSecond()) {
			throwIllegal(FILE_WOULD_BE_EXPIRED);
		}
	}

	private void throwIllegal(IllegalArgumentType type) {
		throw new IllegalArgumentException(type.toString());
	}

	public SpecialFileSystem getSpecialFileSystem() {
		return specialFileSystem;
	}
}
