package com.liang.bbs.user.facade.dto.lottery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrizeConfigDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;          // 奖品名称
    private String tier;          // 层级: TIER_LOW(5-20元), TIER_MID(20-50元), TIER_HIGH(50-300元), TIER_ELITE(300-1000元)
    private String tierName;     // 层级名称
    private Integer level;        // 层级等级: 1=普惠, 2=精选, 3=高级, 4=珍稀
    private String emoji;         // 表情图标
    private String type;         // prize/luck（保持兼容）
}