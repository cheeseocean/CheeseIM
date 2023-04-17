package com.cheeseocean.im.common.entity;

import lombok.Data;

import java.util.List;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
@Data
public class GetMaxAndMinSeqReq {
    private List<String> groupIDList;

    private String userID;

    private String operationID;

    private Object XXXNoUnKeyedLiteral;

    private byte[] XXXUnrecognized;

    private int XXXSizeCache;

}
