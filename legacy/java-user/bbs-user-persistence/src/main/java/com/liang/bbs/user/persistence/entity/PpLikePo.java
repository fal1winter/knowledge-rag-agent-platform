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
public class PpLikePo implements Serializable {
    private Integer id;

    private Integer userId;

    private Integer targetId;

    private String status;

    private Date time;

    private String type;

    private static final long serialVersionUID = 1L;
}