package com.liang.bbs.user.facade.server;

public interface FileUploadService {
    /**
     * 上传文件
     * @param data 文件字节数据
     * @param fileName 文件名
     * @return 文件访问URL
     */
    String upload(byte[] data, String fileName);

    /**
     * 获取文件访问URL
     * @param fileName 文件名
     * @return 文件访问URL
     */
    String getFileUrl(String fileName);
    String fileCutUpload(byte[] bytes, String sourceFileName, String imageType);

    /**
     * 文件上传（按比例压缩）
     *
     * @param bytes
     * @param sourceFileName
     * @param imageType
     * @return
     */
    String fileScaleUpload(byte[] bytes, String sourceFileName, String imageType);
}