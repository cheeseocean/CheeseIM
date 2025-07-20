package com.cheeseocean.im.business.conversation.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 版本日志MongoDB实体类
 * 严格按照OpenIM的VersionLog模型设计
 * 
 * @author CheeseIM
 */
@Document(collection = "version_logs")
@CompoundIndexes({
    @CompoundIndex(name = "user_version_idx", def = "{'user_id': 1, 'version': -1}"),
    @CompoundIndex(name = "user_id_idx", def = "{'user_id': 1}")
})
public class VersionLogMongo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    private String id;
    
    @Field("user_id")
    @Indexed
    private String userID;
    
    @Field("version")
    private Long version;
    
    @Field("logs")
    private String logs;
    
    @Field("create_time")
    private LocalDateTime createTime;
    
    public VersionLogMongo() {
        this.createTime = LocalDateTime.now();
        this.version = 1L;
    }
    
    public VersionLogMongo(String userID, Long version, String logs) {
        this.userID = userID;
        this.version = version;
        this.logs = logs;
        this.createTime = LocalDateTime.now();
    }
    
    // Getter and Setter methods
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUserID() {
        return userID;
    }
    
    public void setUserID(String userID) {
        this.userID = userID;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public String getLogs() {
        return logs;
    }
    
    public void setLogs(String logs) {
        this.logs = logs;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    @Override
    public String toString() {
        return "VersionLogMongo{" +
                "id='" + id + '\'' +
                ", userID='" + userID + '\'' +
                ", version=" + version +
                ", logs='" + logs + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
