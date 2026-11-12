package com.byteharvest.master.client;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/** Talks to the storage nodes' HTTP API: /storeChunk, /getChunk/{id}, /deleteChunk/{id}, /chunks. */
@Component
public class RestStorageNodeClient implements StorageNodeClient {

    private final RestTemplate restTemplate;

    public RestStorageNodeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void storeChunk(String nodeUrl, String chunkId, byte[] data) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chunkId", chunkId);
        body.add("file", new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return "chunk";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        restTemplate.postForEntity(nodeUrl + "/storeChunk", new HttpEntity<>(body, headers), String.class);
    }

    @Override
    public byte[] fetchChunk(String nodeUrl, String chunkId) {
        return restTemplate.getForObject(nodeUrl + "/getChunk/" + chunkId, byte[].class);
    }

    @Override
    public void deleteChunk(String nodeUrl, String chunkId) {
        restTemplate.delete(nodeUrl + "/deleteChunk/" + chunkId);
    }

    @Override
    public List<String> listChunkIds(String nodeUrl) {
        String[] ids = restTemplate.getForObject(nodeUrl + "/chunks", String[].class);
        return ids == null ? List.of() : Arrays.asList(ids);
    }
}
