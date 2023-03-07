/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.platform.state;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.jackson.HashDeserializer;
import com.swirlds.common.jackson.InstantDeserializer;
import com.swirlds.platform.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

/**
 * Defines all data related to the emergency recovery file and how it is formatted.
 */
public record EmergencyRecoveryFile(Recovery recovery) {
    private static final String OUTPUT_FILENAME = "emergencyRecovery.yaml";
    private static final String INPUT_FILENAME = Settings.getInstance().getEmergencyRecoveryStateFileName();

    /**
     * Defines all data related to the emergency recovery file and how it is formatted.
     *
     * @param round     the round number of the state this file is for
     * @param hash      the hash of the state this file is for
     * @param timestamp the consensus timestamp of the state this file is for
     */
    public EmergencyRecoveryFile(final long round, final Hash hash, final Instant timestamp) {
        this(new Recovery(new State(round, hash, timestamp)));
    }

    /**
     * @return the round number of the state this file is for
     */
    public long round() {
        return recovery().state().round();
    }

    /**
     * @return the hash of the state this file is for
     */
    public Hash hash() {
        return recovery().state().hash();
    }

    /**
     * @return the consensus timestamp of the state this file is for
     */
    public Instant timestamp() {
        return recovery().state().timestamp();
    }

    /**
     * Write the data in this record to a yaml file at the specified directory.
     *
     * @param directory the directory to write to. Must exist and be writable.
     * @throws IOException if an exception occurs creating or writing to the file
     */
    public void write(final Path directory) throws IOException {
        final ObjectMapper mapper =
                new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        mapper.writeValue(directory.resolve(OUTPUT_FILENAME).toFile(), this);
    }

    /**
     * Creates a record with the data contained in the emergency recovery file in the directory specified, or null if
     * the file does not exist.
     *
     * @param directory the directory containing the emergency recovery file. Must exist and be readable.
     * @return a new record containing the emergency recovery data in the file, or null if no emergency recovery file
     * exists
     * @throws IOException if an exception occurs reading from the file, or the file content is not properly formatted
     */
    public static EmergencyRecoveryFile read(final Path directory) throws IOException {
        final Path fileToRead = directory.resolve(INPUT_FILENAME);
        if (!Files.exists(fileToRead)) {
            return null;
        }
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);
        return mapper.readValue(fileToRead.toFile(), EmergencyRecoveryFile.class);
    }

    public record Recovery(State state) {}

    public record State(
            long round,
            @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = HashDeserializer.class)
            Hash hash,
            @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = InstantDeserializer.class)
            Instant timestamp) {}

}
