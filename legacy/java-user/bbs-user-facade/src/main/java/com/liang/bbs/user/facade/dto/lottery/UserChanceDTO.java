package com.liang.bbs.user.facade.dto.lottery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserChanceDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long userId;
    private String userName;
    private Integer chances;
}
