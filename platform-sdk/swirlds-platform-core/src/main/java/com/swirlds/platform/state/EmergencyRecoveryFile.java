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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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

/**
 * Defines all data related to the emergency recovery file and how it is formatted.
 */
public record EmergencyRecoveryFile(Recovery recovery) {
    private static final String OUTPUT_FILENAME = "emergencyRecovery.yaml";
    private static final String INPUT_FILENAME = Settings.getInstance().getEmergencyRecoveryStateFileName();

    /**
     * Creates a new emergency recovery file with data about a state being written to disk in normal operation.
     *
     * @param round     the round number of the state this file is for
     * @param hash      the hash of the state this file is for
     * @param timestamp the consensus timestamp of the state this file is for
     */
    public EmergencyRecoveryFile(final long round, final Hash hash, final Instant timestamp) {
        this(new Recovery(new State(round, hash, timestamp), null));
    }

    /**
     * Creates a new emergency recovery file with data about the state resulting from event recovery disk and the
     * consensus time of the bootstrap state used to perform event recovery.
     *
     * @param state         emergency recovery data for the state resulting from the event recovery process
     * @param bootstrapTime the consensus timestamp of the bootstrap state used to start the event recovery process
     */
    public EmergencyRecoveryFile(final State state, final Instant bootstrapTime) {
        this(new Recovery(state, new Boostrap(bootstrapTime)));
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
     * @return the YAML structure of the emergency recovery file
     */
    public Recovery recovery() {
        return recovery;
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
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        final EmergencyRecoveryFile file = mapper.readValue(fileToRead.toFile(), EmergencyRecoveryFile.class);
        validate(file);
        return file;
    }

    private static void validate(final EmergencyRecoveryFile file) throws IOException {
        if (file == null) {
            throw new IOException("Failed to read emergency recovery file, object mapper returned null value");
        }

        if (file.hash() == null) {
            throw new IOException("Required field 'hash' is null.");
        }
    }

    /**
     * The top level of the emergency recovery YAML structure.
     *
     * @param state    information about the state written to disk
     * @param boostrap information about the state used to bootstrap event recovery. Not written during normal
     *                 operation. Only written during event recovery.
     */
    public record Recovery(State state, Boostrap boostrap) {}

    /**
     * Data about the state written to disk, either during normal operation or at the end of event recovery.
     *
     * @param round     the round of the state. This value is required by the platform when reading a file.
     * @param hash      the hash of the state. This value is required by the platform when reading a file.
     * @param timestamp the consensus timestamp of the state. This value is optional for the platform when reading a
     *                  file, but should always be populated with an accurate value when written by the platform.
     */
    public record State(
            long round,
            @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = HashDeserializer.class)
            Hash hash,
            @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = InstantDeserializer.class)
            Instant timestamp) {}

    /**
     * Data about the bootstrap state loaded during event recovery (the starting state)
     *
     * @param timestamp the consensus timestamp of the bootstrap state
     */
    public record Boostrap(
            @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = InstantDeserializer.class)
            Instant timestamp) {}

}
