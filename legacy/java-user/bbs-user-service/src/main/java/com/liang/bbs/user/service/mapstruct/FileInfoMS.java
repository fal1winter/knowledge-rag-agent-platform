package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.FileInfoDTO;
import com.liang.bbs.user.persistence.entity.FileInfoPo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface FileInfoMS extends CommonMS<FileInfoPo, FileInfoDTO> {
    FileInfoMS INSTANCE = Mappers.getMapper(FileInfoMS.class);
}
