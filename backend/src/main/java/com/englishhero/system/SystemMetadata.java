package com.englishhero.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("system_metadata")
public class SystemMetadata {
    @TableId(value = "metadata_key", type = IdType.INPUT)
    private String metadataKey;
    @TableField("metadata_value")
    private String metadataValue;

    public String getMetadataKey() { return metadataKey; }
    public void setMetadataKey(String metadataKey) { this.metadataKey = metadataKey; }
    public String getMetadataValue() { return metadataValue; }
    public void setMetadataValue(String metadataValue) { this.metadataValue = metadataValue; }
}
