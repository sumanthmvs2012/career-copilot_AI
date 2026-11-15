package com.careercopilot.common.storage;

import java.io.InputStream;

/** Saves and reads files by a server-generated key. Local disk today, cloud storage later. */
public interface StorageService {

    /** Saves the stream under the key and returns the key. */
    String store(String key, InputStream in, long size);

    InputStream open(String key);

    /** Removes the file; a missing file is not an error. */
    void delete(String key);
}
