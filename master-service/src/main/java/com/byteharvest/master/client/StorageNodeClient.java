package com.byteharvest.master.client;

import java.util.List;

/**
 * Everything the master can ask a storage node to do. Services depend on this interface, not on
 * RestTemplate or on the node's URL layout, so the transport can change (or be faked in a test)
 * without touching the services.
 *
 * Every method throws a RuntimeException if the node cannot be reached or refuses the request.
 */
public interface StorageNodeClient {

    /** Writes the chunk to the node, overwriting any copy it already has. */
    void storeChunk(String nodeUrl, String chunkId, byte[] data);

    /** Reads the chunk from the node. May return null if the node answers with an empty body. */
    byte[] fetchChunk(String nodeUrl, String chunkId);

    void deleteChunk(String nodeUrl, String chunkId);

    /** The ids of every chunk the node currently holds. */
    List<String> listChunkIds(String nodeUrl);
}
