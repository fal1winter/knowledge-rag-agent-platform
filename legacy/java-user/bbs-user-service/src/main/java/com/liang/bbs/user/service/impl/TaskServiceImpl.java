package com.liang.bbs.user.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.TaskDTO;
import com.liang.bbs.user.facade.server.TaskService;
import com.liang.bbs.user.persistence.entity.TaskPo;
import com.liang.bbs.user.persistence.entity.TaskPoExample;
import com.liang.bbs.user.persistence.mapper.TaskPoMapper;
import com.liang.bbs.user.service.mapstruct.TaskMS;
import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.common.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Task服务实现类
 */
@Slf4j
@Service
@Component
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskPoMapper taskPoMapper;

    @Override
    public ResponseResult<Boolean> createTask(TaskDTO taskDTO) {
        try {
            TaskPo taskPo = TaskMS.INSTANCE.toPo(taskDTO);
            taskPo.setSendtime(new Date());
            taskPo.setStatus("pending");
            int result = taskPoMapper.insertSelective(taskPo);
            return ResponseResult.success(result > 0);
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ResponseResult.build(ResponseCode.OPERATE_FAIL, false);
        }
    }

    @Override
    public ResponseResult<Boolean> updateTask(TaskDTO taskDTO) {
        try {
            if (taskDTO.getId() == null) {
                return ResponseResult.build(ResponseCode.PARAM_ERROR, false);
            }
            TaskPo taskPo = TaskMS.INSTANCE.toPo(taskDTO);
            int result = taskPoMapper.updateByPrimaryKeySelective(taskPo);
            return ResponseResult.success(result > 0);
        } catch (Exception e) {
            log.error("更新任务失败", e);
            return ResponseResult.build(ResponseCode.UPDATE_FAILED, false);
        }
    }

    @Override
    public ResponseResult<Boolean> deleteTask(Integer id) {
        try {
            int result = taskPoMapper.deleteByPrimaryKey(id);
            return ResponseResult.success(result > 0);
        } catch (Exception e) {
            log.error("删除任务失败", e);
            return ResponseResult.build(ResponseCode.OPERATE_FAIL, false);
        }
    }

    @Override
    public ResponseResult<TaskDTO> getTaskById(Integer id) {
        try {
            TaskPo taskPo = taskPoMapper.selectByPrimaryKey(id);
            if (taskPo == null) {
                return ResponseResult.build(ResponseCode.NOT_EXISTS, null);
            }
            TaskDTO taskDTO = TaskMS.INSTANCE.toDTO(taskPo);
            return ResponseResult.success(taskDTO);
        } catch (Exception e) {
            log.error("获取任务失败", e);
            return ResponseResult.build(ResponseCode.SYSTEM_EXCEPTION, null);
        }
    }

    @Override
    public ResponseResult<PageInfo<TaskDTO>> getTaskList(Integer pageNum, Integer pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            List<TaskPo> taskPos = taskPoMapper.selectByExample(null);
            PageInfo<TaskPo> pageInfoPo = new PageInfo<>(taskPos);
            PageInfo<TaskDTO> taskDTOs = TaskMS.INSTANCE.toPage(pageInfoPo);
            
            return ResponseResult.success(taskDTOs);
        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return ResponseResult.build(ResponseCode.SYSTEM_EXCEPTION, null);
        }
    }

    @Override
    public ResponseResult<PageInfo<TaskDTO>> getTasksByStatus(String status, Integer pageNum, Integer pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            // 这里应该使用TaskPoExample进行条件查询，简化处理
            TaskPoExample example=new TaskPoExample();
            example.createCriteria().andStatusEqualTo(status);
            List<TaskPo> taskPos = taskPoMapper.selectByExample(null);
            PageInfo<TaskPo> pageInfoPo = new PageInfo<>(taskPos);
            PageInfo<TaskDTO> taskDTOs = TaskMS.INSTANCE.toPage(pageInfoPo);
            
            return ResponseResult.success(taskDTOs);
        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return ResponseResult.build(ResponseCode.SYSTEM_EXCEPTION, null);
        }
    }

    @Override
    public ResponseResult<Boolean> assignTask(Integer taskId, Integer workerId) {
        try {
            TaskPo taskPo = new TaskPo();
            taskPo.setId(taskId);
            taskPo.setWorkerid(workerId);
            taskPo.setStatus("assigned");
            int result = taskPoMapper.updateByPrimaryKeySelective(taskPo);
            return ResponseResult.success(result > 0);
        } catch (Exception e) {
            log.error("分配任务失败", e);
            return ResponseResult.build(ResponseCode.OPERATE_FAIL, false);
        }
    }

    @Override
    public ResponseResult<Boolean> completeTask(Integer taskId) {
        try {
            TaskPo taskPo = new TaskPo();
            taskPo.setId(taskId);
            taskPo.setStatus("completed");
            taskPo.setFinishtime(new Date());
            int result = taskPoMapper.updateByPrimaryKeySelective(taskPo);
            return ResponseResult.success(result > 0);
        } catch (Exception e) {
            log.error("完成任务失败", e);
            return ResponseResult.build(ResponseCode.OPERATE_FAIL, false);
        }
    }

    @Override
    public ResponseResult<PageInfo<TaskDTO>> getTasksBySender(Integer senderId, Integer pageNum, Integer pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            // 这里应该使用TaskPoExample进行条件查询，简化处理
            List<TaskPo> taskPos = taskPoMapper.selectByExample(null);
            PageInfo<TaskPo> pageInfoPo = new PageInfo<>(taskPos);
            List<TaskDTO> taskDTOs = TaskMS.INSTANCE.toDTO(taskPos);
            PageInfo<TaskDTO> pageInfo = new PageInfo<>(taskDTOs);
            pageInfo.setTotal(pageInfoPo.getTotal());
            pageInfo.setPages(pageInfoPo.getPages());
            pageInfo.setPageNum(pageInfoPo.getPageNum());
            pageInfo.setPageSize(pageInfoPo.getPageSize());
            pageInfo.setHasNextPage(pageInfoPo.isHasNextPage());
            return ResponseResult.success(pageInfo);
        } catch (Exception e) {
            log.error("获取创建任务列表失败", e);
            return ResponseResult.build(ResponseCode.SYSTEM_EXCEPTION, null);
        }
    }

    @Override
    public ResponseResult<PageInfo<TaskDTO>> getTasksByWorker(Integer workerId, Integer pageNum, Integer pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            // 这里应该使用TaskPoExample进行条件查询，简化处理
            List<TaskPo> taskPos = taskPoMapper.selectByExample(null);
            PageInfo<TaskPo> pageInfoPo = new PageInfo<>(taskPos);
            List<TaskDTO> taskDTOs = TaskMS.INSTANCE.toDTO(taskPos);
            PageInfo<TaskDTO> pageInfo = new PageInfo<>(taskDTOs);
            pageInfo.setTotal(pageInfoPo.getTotal());
            pageInfo.setPages(pageInfoPo.getPages());
            pageInfo.setPageNum(pageInfoPo.getPageNum());
            pageInfo.setPageSize(pageInfoPo.getPageSize());
            pageInfo.setHasNextPage(pageInfoPo.isHasNextPage());
            return ResponseResult.success(pageInfo);
        } catch (Exception e) {
            log.error("获取分配任务列表失败", e);
            return ResponseResult.build(ResponseCode.SYSTEM_EXCEPTION, null);
        }
    }
}