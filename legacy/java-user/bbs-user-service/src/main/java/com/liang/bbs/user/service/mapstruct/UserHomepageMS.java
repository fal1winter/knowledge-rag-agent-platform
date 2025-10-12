package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.UserHomepageDTO;
import com.liang.bbs.user.persistence.entity.UserHomepagePo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 用户主页对象转换器
 */
@Mapper(componentModel = "spring")
public interface UserHomepageMS extends CommonMS<UserHomepagePo, UserHomepageDTO> {
    UserHomepageMS INSTANCE = Mappers.getMapper(UserHomepageMS.class);
}
