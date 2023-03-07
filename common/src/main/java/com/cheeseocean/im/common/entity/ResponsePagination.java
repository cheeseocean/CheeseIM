package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class ResponsePagination {
    private int currentPage;
    private int showNumber;

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getShowNumber() {
        return showNumber;
    }

    public void setShowNumber(int showNumber) {
        this.showNumber = showNumber;
    }
}
