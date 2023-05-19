package com.swirlds.platform.recovery.emergencyfile;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.jackson.HashDeserializer;

import java.net.URL;

public record Location(
		String type,
		URL url,
		@JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = HashDeserializer.class) Hash hash
) {
}
