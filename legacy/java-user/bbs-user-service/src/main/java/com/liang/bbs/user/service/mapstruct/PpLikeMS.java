package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.PpLikeDTO;
import com.liang.bbs.user.persistence.entity.PpLikePo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 点赞实体与DTO的映射器
 */
@Mapper(componentModel = "spring")
public interface PpLikeMS extends CommonMS<PpLikePo, PpLikeDTO>{
    
    PpLikeMS INSTANCE = Mappers.getMapper(PpLikeMS.class);

}