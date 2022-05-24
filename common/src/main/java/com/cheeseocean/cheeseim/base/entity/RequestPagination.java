package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class RequestPagination {
    private int pageNumber;
    private int showNumber;

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getShowNumber() {
        return showNumber;
    }

    public void setShowNumber(int showNumber) {
        this.showNumber = showNumber;
    }
}
