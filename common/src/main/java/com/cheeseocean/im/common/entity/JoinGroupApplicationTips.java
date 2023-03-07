package com.cheeseocean.im.common.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class JoinGroupApplicationTips {
    private GroupInfo group;
    private PublicUserInfo applicant;
    private String reqMsg;

    public GroupInfo getGroup() {
        return group;
    }

    public void setGroup(GroupInfo group) {
        this.group = group;
    }

    public PublicUserInfo getApplicant() {
        return applicant;
    }

    public void setApplicant(PublicUserInfo applicant) {
        this.applicant = applicant;
    }

    public String getReqMsg() {
        return reqMsg;
    }

    public void setReqMsg(String reqMsg) {
        this.reqMsg = reqMsg;
    }
}
