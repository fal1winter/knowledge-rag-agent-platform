package com.liang.bbs.user.facade.dto;

import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileInfoDTO implements Serializable {
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