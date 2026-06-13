package com.byteharvest.master.model;

public class HeartbeatRequest {

    private String nodeId;
    private String nodeUrl;

    public HeartbeatRequest() {
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
    }
}
