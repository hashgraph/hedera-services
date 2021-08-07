package com.hedera.services.state.merkle.v3.files;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable class representing a set of indexed data files. It is basically a copy on write array with one
 * specialization that the first index doesn't have to be 0.
 *
 * The files list is not expected to be contiguous so might contain nulls.
 */
class IndexedFileList {
    private final int firstFileIndex;
    private final DataFileReader[] files;

    private IndexedFileList(int firstFileIndex, DataFileReader[] files) {
        this.firstFileIndex = firstFileIndex;
        this.files = files;
    }

    /**
     * Create a new IndexedFileList containing the data from oldIndexedFileList with newDataFileReader added it
     * its index.
     */
    public static IndexedFileList withAddedFile(IndexedFileList oldList, DataFileReader newDataFileReader) {
        int newFirstFileIndex;
        DataFileReader[] newFiles;
        if (oldList == null) {
            newFirstFileIndex = newDataFileReader.getMetadata().getIndex();
            newFiles = new DataFileReader[]{newDataFileReader};
        } else {
            final int newFilesIndex = newDataFileReader.getMetadata().getIndex();
            if (newFilesIndex <  oldList.firstFileIndex) { // new file is before first old file
                // adding an earlier file
                int shiftRight = oldList.firstFileIndex - newFilesIndex;
                // create new array
                newFiles = new DataFileReader[oldList.files.length + shiftRight];
                // copy over old files shifted
                System.arraycopy(oldList.files,0,newFiles,shiftRight,oldList.files.length);
                // add new file
                newFiles[0] = newDataFileReader;
                // update new first index
                newFirstFileIndex = newFilesIndex;
            } else if (newFilesIndex <= oldList.getLastFileIndex()) { // new file is within old range so just update array
                // create new array
                newFiles = new DataFileReader[oldList.files.length];
                // copy over old files shifted
                System.arraycopy(oldList.files,0,newFiles,0,oldList.files.length);
                // add new file
                newFiles[newFilesIndex - oldList.firstFileIndex] = newDataFileReader;
                // update new first index
                newFirstFileIndex = oldList.firstFileIndex;
            } else { // new file is after old array
                int newArraySize = newFilesIndex - oldList.firstFileIndex + 1;
                // create new array
                newFiles = new DataFileReader[newArraySize];
                // copy over old files shifted
                System.arraycopy(oldList.files,0,newFiles,0,oldList.files.length);
                // add new file
                newFiles[newFilesIndex - oldList.firstFileIndex] = newDataFileReader;
                // update new first index
                newFirstFileIndex = oldList.firstFileIndex;
            }
        }
        return new IndexedFileList(newFirstFileIndex,newFiles);
    }

    /**
     * Create a new IndexedFileList from existing set of files. It is assumed the files are sorted by index, but
     * they are not expected to be contiguous.
     */
    public static IndexedFileList withExistingFiles(DataFileReader[] files) {
        int firstFileIndex = files[0].getMetadata().getIndex();
        int maxIndex = files[files.length-1].getMetadata().getIndex();
        DataFileReader[] newFiles;
        if (files.length == maxIndex-firstFileIndex+1) {
            // files are contiguous so it is simple
            newFiles = files;
        } else {
            newFiles = new DataFileReader[maxIndex-firstFileIndex+1];
            for (var file: files) {
                newFiles[file.getMetadata().getIndex()-firstFileIndex] = file;
            }
        }
        return new IndexedFileList(firstFileIndex,newFiles);
    }

    /**
     * Create a new IndexedFileList with all existing files in oldIndexedFileList that are not in filesToDelete.
     *
     * @param oldIndexedFileList original IndexedFileList, can not be null
     * @param filesToDelete non-null list of files to delete
     * @return new IndexedFileList with just remaining files
     */
    public static IndexedFileList withDeletingFiles(IndexedFileList oldIndexedFileList, List<DataFileReader> filesToDelete) {
        List<DataFileReader> newFileList = new ArrayList<>(oldIndexedFileList.files.length);
        for(var file: oldIndexedFileList.files) {
            if (!filesToDelete.contains(file)) newFileList.add(file);
        }
        // now create new IndexedFileList
        if (newFileList.isEmpty()) {
            return null;
        } else {
            return new IndexedFileList(newFileList.get(0).getMetadata().getIndex(), newFileList.toArray(new DataFileReader[0]));
        }
    }

    /**
     * Get the last file index
     */
    public int getLastFileIndex() {
        return firstFileIndex + files.length -1;
    }

    /**
     * Get the last file
     */
    public DataFileReader getLastFile() {
        // it is assumed that although files can contain nulls the first and last are always non-null
        return files[files.length-1];
    }

    /**
     * Get the file at given fileIndex.
     */
    public DataFileReader getFile(int fileIndex) {
        if (fileIndex < firstFileIndex) return null;
        final int localIndex = fileIndex-firstFileIndex;
        if (localIndex >= files.length) return null;
        return files[localIndex];
    }

    /**
     * Get a collection containing all non-null files. The indexes in the list will not be file index.
     */
    public List<DataFileReader> getAllFiles() {
        final List<DataFileReader> allReadOnlyFiles = new ArrayList<>(files.length);
        for (var file : files) if (file != null) allReadOnlyFiles.add(file);
        return allReadOnlyFiles;
    }

    /**
     * Close all file readers we have
     */
    public void closeAll() throws IOException {
        for(var file:files) if (file!=null) file.close();
    }
}