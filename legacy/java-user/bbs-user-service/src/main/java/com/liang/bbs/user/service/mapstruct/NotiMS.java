package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.persistence.entity.NotiPo;
import com.liang.bbs.user.facade.dto.NotiDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Noti MapStruct 映射器
 *
 */
@Mapper(componentModel = "spring")
public interface NotiMS extends CommonMS<NotiPo, NotiDTO> {
    
    NotiMS INSTANCE = Mappers.getMapper(NotiMS.class);
}