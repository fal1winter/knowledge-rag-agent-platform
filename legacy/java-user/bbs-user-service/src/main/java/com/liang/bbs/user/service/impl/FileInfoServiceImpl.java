package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.dto.FileInfoDTO;
import com.liang.bbs.user.facade.server.FileInfoService;
import com.liang.bbs.user.persistence.entity.FileInfoPo;
import com.liang.bbs.user.persistence.entity.FileInfoPoExample;
import com.liang.bbs.user.persistence.mapper.FileInfoPoMapper;
import com.liang.bbs.user.service.mapstruct.FileInfoMS;

import java.util.List;

import org.apache.dubbo.config.annotation.Service;

@Service
public class FileInfoServiceImpl implements FileInfoService {

    private final FileInfoPoMapper fileInfoPoMapper;

    public FileInfoServiceImpl(FileInfoPoMapper fileInfoPoMapper) {
        this.fileInfoPoMapper = fileInfoPoMapper;
    }

    @Override
    public int upload(FileInfoDTO fileInfoDTO) {
        FileInfoPo fileInfoPo = new FileInfoPo();
        fileInfoPo = FileInfoMS.INSTANCE.toPo(fileInfoDTO);
        return fileInfoPoMapper.insert(fileInfoPo);
        
    }

    @Override
    public FileInfoDTO getById(Integer id) {
        FileInfoPo fileInfoPo = fileInfoPoMapper.selectByPrimaryKey(id);
        return FileInfoMS.INSTANCE.toDTO(fileInfoPo);
    }

    @Override
    public List<FileInfoDTO> getList() {
        FileInfoPoExample fileInfoExample = new FileInfoPoExample();
        return FileInfoMS.INSTANCE.toDTO(fileInfoPoMapper.selectByExample(fileInfoExample));
    }

    @Override
    public int delete(Integer id) {
        return fileInfoPoMapper.deleteByPrimaryKey(id);
    }
}
