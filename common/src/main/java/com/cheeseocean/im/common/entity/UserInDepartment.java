package com.cheeseocean.im.common.entity;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class UserInDepartment {
    private OrganizationUser organizationUser;
    private List<DepartmentMember> departmentMember;

    public OrganizationUser getOrganizationUser() {
        return organizationUser;
    }

    public void setOrganizationUser(OrganizationUser organizationUser) {
        this.organizationUser = organizationUser;
    }

    public List<DepartmentMember> getDepartmentMember() {
        return departmentMember;
    }

    public void setDepartmentMember(List<DepartmentMember> departmentMember) {
        this.departmentMember = departmentMember;
    }
}
