package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LotteryChancePo implements Serializable {
    private Long id;
    private Long userId;
    private Integer chances;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
