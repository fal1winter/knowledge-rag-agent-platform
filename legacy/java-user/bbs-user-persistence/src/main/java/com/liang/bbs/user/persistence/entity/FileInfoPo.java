package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileInfoPo implements Serializable {
    private Integer id;

    private Integer userId;

    private String fileName;

    private Integer fileSize;

    private String fileType;

    private String storagePath;

    private String remark;

    private Date uploadTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}