package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.server.FileUploadService;
import com.liang.bbs.user.service.utils.QiniuUtils;
import org.apache.dubbo.config.annotation.Service;

@Service
public class FileUploadServiceImpl implements FileUploadService {
    @Override
    public String upload(byte[] data, String fileName) {
        
        return QiniuUtils.uploadBytes(data, fileName);
    }

    @Override
    public String getFileUrl(String fileName) {
        return QiniuUtils.getPublicFileUrl(fileName);
    }
    @Override
    public String fileCutUpload(byte[] bytes, String sourceFileName, String imageType){
        return "true";
    };

    /**
     * 文件上传（按比例压缩）
     *
     * @param bytes
     * @param sourceFileName
     * @param imageType
     * @return
     */
    @Override
    public String fileScaleUpload(byte[] bytes, String sourceFileName, String imageType){
        return null;
    };
}