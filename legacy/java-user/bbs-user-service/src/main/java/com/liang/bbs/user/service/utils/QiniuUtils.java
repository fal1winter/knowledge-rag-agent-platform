package com.liang.bbs.user.service.utils;

import org.springframework.beans.factory.annotation.Value;

import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.storage.model.FetchRet;
import com.qiniu.util.Auth;


public class QiniuUtils {

    private static final String ACCESS_KEY = "mg6kH2HsWQggVdiNx-NCadOCISFZduSst9dBgJmx";
    private static final String SECRET_KEY = "i4mjrdNy8xiTk8FII42DKbMCt_F4I_wxP-uli1J3";
    private static final String BUCKET = "ppvt";

    private static final Auth auth = Auth.create(ACCESS_KEY, SECRET_KEY);
    private static final Configuration cfg = new Configuration(com.qiniu.storage.Region.huadong()); // 根据区域修改
    private static final UploadManager uploadManager = new UploadManager(cfg);
private static final BucketManager bucketManager = new BucketManager(auth, cfg);
    /**
     * 获取上传凭证
     */
    public static String getUploadToken() {
        return auth.uploadToken(BUCKET);
    }

    /**
     * 上传字节数组
     * @param data 字节数据
     * @param key  上传到七牛云的文件名（可为 null，null 表示用 hash 作为文件名）
     */
    public static String uploadBytes(byte[] data, String key) {
        try {
            String upToken = getUploadToken();
            Response response = uploadManager.put(data, key, upToken);
            System.out.println("七牛云响应: " + response.bodyString());
            DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
            return putRet.key; // 返回文件名
        } catch (QiniuException ex) {
            Response r = ex.response;
            System.err.println(r.toString());
            try {
                System.err.println(r.bodyString());
            } catch (QiniuException ignore) {}
        }
        return null;
    }
    public static String FetchFile(String url,String name){
        try{
            FetchRet res=bucketManager.fetch(url, BUCKET, name);
return res.toString();


        }catch(Exception e){
            System.err.println(e.toString());
            return null;
        }
    }

    /**
     * 获取文件访问 URL（公有空间）
     */
    public static String getPublicFileUrl(String key) {
        return "http://papervote.top/" + key;
    }

    /**
     * 获取私有空间访问 URL
     */
    public static String getPrivateFileUrl(String key) {
        long expireInSeconds = 3600; // 1小时
        return auth.privateDownloadUrl("http://papervote.top/" + key, expireInSeconds);
    }
}
