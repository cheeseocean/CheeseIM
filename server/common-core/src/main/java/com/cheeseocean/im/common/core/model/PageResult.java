package com.cheeseocean.im.common.core.model;

import java.util.ArrayList;
import java.util.List;

public class PageResult<T> {

    private List<T> records = new ArrayList<>();
    private long total;

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records == null ? new ArrayList<>() : new ArrayList<>(records);
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
