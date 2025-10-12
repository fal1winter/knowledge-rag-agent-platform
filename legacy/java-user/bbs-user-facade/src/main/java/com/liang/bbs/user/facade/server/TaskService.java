package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.TaskDTO;
import com.liang.bbs.common.web.basic.ResponseResult;

import java.util.List;

/**
 * Task服务接口
 */
public interface TaskService {

    /**
     * 创建任务
     * @param taskDTO 任务信息
     * @return 创建结果
     */
    ResponseResult<Boolean> createTask(TaskDTO taskDTO);

    /**
     * 更新任务
     * @param taskDTO 任务信息
     * @return 更新结果
     */
    ResponseResult<Boolean> updateTask(TaskDTO taskDTO);

    /**
     * 删除任务
     * @param id 任务ID
     * @return 删除结果
     */
    ResponseResult<Boolean> deleteTask(Integer id);

    /**
     * 根据ID获取任务
     * @param id 任务ID
     * @return 任务信息
     */
    ResponseResult<TaskDTO> getTaskById(Integer id);

    /**
     * 获取任务列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 任务列表
     */
    ResponseResult<PageInfo<TaskDTO>> getTaskList(Integer pageNum, Integer pageSize);

    /**
     * 根据状态获取任务列表
     * @param status 任务状态
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 任务列表
     */
    ResponseResult<PageInfo<TaskDTO>> getTasksByStatus(String status, Integer pageNum, Integer pageSize);

    /**
     * 分配任务给工作人员
     * @param taskId 任务ID
     * @param workerId 工作人员ID
     * @return 分配结果
     */
    ResponseResult<Boolean> assignTask(Integer taskId, Integer workerId);

    /**
     * 完成任务
     * @param taskId 任务ID
     * @return 完成结果
     */
    ResponseResult<Boolean> completeTask(Integer taskId);

    /**
     * 获取用户创建的任务
     * @param senderId 创建者ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 任务列表
     */
    ResponseResult<PageInfo<TaskDTO>> getTasksBySender(Integer senderId, Integer pageNum, Integer pageSize);

    /**
     * 获取工作人员的任务
     * @param workerId 工作人员ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 任务列表
     */
    ResponseResult<PageInfo<TaskDTO>> getTasksByWorker(Integer workerId, Integer pageNum, Integer pageSize);
}