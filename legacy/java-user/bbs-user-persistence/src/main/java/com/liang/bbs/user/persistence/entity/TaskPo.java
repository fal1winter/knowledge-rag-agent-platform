package com.liang.bbs.user.persistence.entity;

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
public class TaskPo implements Serializable {
    private Integer id;

    private String task;

    private String status;

    private Integer senderid;

    private String bio;

    private Date sendtime;

    private Date finishtime;

    private Integer workerid;

    private static final long serialVersionUID = 1L;
}