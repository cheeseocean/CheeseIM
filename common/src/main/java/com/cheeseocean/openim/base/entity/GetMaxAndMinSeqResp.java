package com.cheeseocean.openim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class GetMaxAndMinSeqResp {
   private int maxSeq;
   private int minSeq;

    public int getMaxSeq() {
        return maxSeq;
    }

    public void setMaxSeq(int maxSeq) {
        this.maxSeq = maxSeq;
    }

    public int getMinSeq() {
        return minSeq;
    }

    public void setMinSeq(int minSeq) {
        this.minSeq = minSeq;
    }
}
