package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 
 */
@Data
public class SearchFuzzDTO implements Serializable {
   
    private String name;

   
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    
    private String position;

    private String company;

    private String intro;

    private Boolean state;

    private static final long serialVersionUID = 1L;

}
