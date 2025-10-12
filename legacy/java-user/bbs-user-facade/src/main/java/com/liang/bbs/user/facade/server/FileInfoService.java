package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.FileInfoDTO;


import com.liang.bbs.user.facade.dto.user.UserSsoDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 */

public interface FileInfoService {

   
    int upload(FileInfoDTO fileInfoDTO);

    
    FileInfoDTO getById(Integer id);

    List<FileInfoDTO> getList();

    int delete(Integer id);


}
