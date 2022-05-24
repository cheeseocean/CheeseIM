package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class Department {
    private String departmentID;
    private String avatarURL;
    private String name;
    private String parentID;
    private int order;
    private int departmentType;
    private long createTime;
    private long subDepartmentNum;
    private int memberNum;
    private String ex;

    public String getDepartmentID() {
        return departmentID;
    }

    public void setDepartmentID(String departmentID) {
        this.departmentID = departmentID;
    }

    public String getAvatarURL() {
        return avatarURL;
    }

    public void setAvatarURL(String avatarURL) {
        this.avatarURL = avatarURL;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentID() {
        return parentID;
    }

    public void setParentID(String parentID) {
        this.parentID = parentID;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getDepartmentType() {
        return departmentType;
    }

    public void setDepartmentType(int departmentType) {
        this.departmentType = departmentType;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getSubDepartmentNum() {
        return subDepartmentNum;
    }

    public void setSubDepartmentNum(long subDepartmentNum) {
        this.subDepartmentNum = subDepartmentNum;
    }

    public int getMemberNum() {
        return memberNum;
    }

    public void setMemberNum(int memberNum) {
        this.memberNum = memberNum;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }
}
