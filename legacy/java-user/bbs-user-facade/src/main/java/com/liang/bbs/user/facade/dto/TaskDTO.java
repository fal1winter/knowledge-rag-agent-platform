package com.liang.bbs.user.facade.dto;

import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskDTO implements Serializable {
    private Integer id;

    private String task;

    private String status;

    private Integer senderid;

    private String bio;//简介

    private Date sendtime;//发起时间

    private Date finishtime;//完成时间

    private Integer workerid;//完成人

    private static final long serialVersionUID = 1L;
}