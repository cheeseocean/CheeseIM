package com.cheeseocean.im.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 离线推送信息
 *
 * @author xxxcrel
 */
@Data
public class OfflinePushInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    private String  title;
    /**
     * 标题
     */
    private String  desc;
    /**
     * 附加信息
     */
    private String  ex;
    /**
     * IOS 推送声音
     */
    private String  iOSPushSound;
    /**
     * IOS 小圆点统计
     */
    private Boolean iOSBadgeCount;
    private String  signalInfo;

    private Map<String, Object> pushExtras;

    public OfflinePushInfo() {
        this.iOSPushSound = "default";
        this.iOSBadgeCount = true;
    }

    public OfflinePushInfo(String title, String desc) {
        this();
        this.title = title;
        this.desc = desc;
    }
}
