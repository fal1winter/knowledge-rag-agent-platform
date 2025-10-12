package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
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
public class NotiPo implements Serializable {
    private Integer id;

    private Integer userid;

    private String type;

    private String content;

    private LocalDateTime time;

    private String status;

    private LocalDateTime expiretime;

    private Integer senderid;

    private String isRead;

    private String extra;

    private static final long serialVersionUID = 1L;
}