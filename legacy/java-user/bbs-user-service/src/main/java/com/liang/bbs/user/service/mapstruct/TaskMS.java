package com.liang.bbs.user.service.mapstruct;
import com.liang.bbs.user.facade.dto.TaskDTO;
import com.liang.bbs.user.persistence.entity.TaskPo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
/**
 */
@Mapper(componentModel = "spring")
public interface TaskMS extends CommonMS<TaskPo, TaskDTO> {
    TaskMS INSTANCE = Mappers.getMapper(TaskMS.class);
}
