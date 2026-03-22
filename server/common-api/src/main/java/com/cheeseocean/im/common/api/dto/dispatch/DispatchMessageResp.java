package com.cheeseocean.im.common.api.dto.dispatch;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DispatchMessageResp implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<DispatchResult> results = new ArrayList<>();

    public List<DispatchResult> getResults() {
        return results;
    }

    public void setResults(List<DispatchResult> results) {
        this.results = results == null ? new ArrayList<>() : new ArrayList<>(results);
    }
}
