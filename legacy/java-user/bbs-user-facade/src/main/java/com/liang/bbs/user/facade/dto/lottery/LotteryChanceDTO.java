package com.liang.bbs.user.facade.dto.lottery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LotteryChanceDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long userId;
    private Integer chances;
    private LocalDateTime updateTime;
}
