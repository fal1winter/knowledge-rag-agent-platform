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
public class FollowPo implements Serializable {
    private Integer id;

    private String type;

    private Integer targetid;

    private Integer userid;

    private Date time;

    private String status;

    private static final long serialVersionUID = 1L;
}