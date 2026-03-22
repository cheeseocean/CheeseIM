package com.cheeseocean.im.common.core.model;

import java.util.List;

public class PageResult<T> {

    private List<T> records = List.of();
    private long total;

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records == null ? List.of() : List.copyOf(records);
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
