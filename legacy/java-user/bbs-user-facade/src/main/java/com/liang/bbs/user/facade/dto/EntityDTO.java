package com.liang.bbs.user.facade.dto;
import lombok.Data;

import java.io.Serializable;

@Data
public class EntityDTO implements Serializable {
    private Integer id;
    private String type;
    private Object dto;
    private static final long serialVersionUID = 1L;

}
