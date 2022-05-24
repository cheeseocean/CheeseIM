package com.cheeseocean.cheeseim.base.entity;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class UserDepartmentMember {
    private OrganizationUser organizationUser;
    private DepartmentMember departmentMember;

    public OrganizationUser getOrganizationUser() {
        return organizationUser;
    }

    public void setOrganizationUser(OrganizationUser organizationUser) {
        this.organizationUser = organizationUser;
    }

    public DepartmentMember getDepartmentMember() {
        return departmentMember;
    }

    public void setDepartmentMember(DepartmentMember departmentMember) {
        this.departmentMember = departmentMember;
    }
}
