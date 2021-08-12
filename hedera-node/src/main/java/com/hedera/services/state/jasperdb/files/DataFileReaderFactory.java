package com.hedera.services.state.jasperdb.files;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory for creating data file readers
 */
public interface DataFileReaderFactory {

    /**
     * Open an existing data file, reading the metadata from the file
     *
     * @param path the path to the data file
     */
    DataFileReader newDataFileReader(Path path) throws IOException;

    /**
     * Open an existing data file, using the provided metadata
     *
     * @param path the path to the data file
     * @param metadata the file's metadata to save loading from file
     */
    DataFileReader newDataFileReader(Path path, DataFileMetadata metadata) throws IOException;
}
