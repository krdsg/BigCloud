package com.kongrui.model;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-10
 * Time: 下午9:25
 * To change this template use File | Settings | File Templates.
 */
public class Const {
    public static String ROOTPATH = "e:\\test1";
    public static String UPLOAD_ROOTPATH = "/apps/krdsgtest";
    public static String FTP_ROOTPATH = "/myBigCloud";
    public static String PROPERTIES_FILENAME = "propTestDownload.properties";
    public static enum UploadStatus{
        WaitToUpload("-1","等待上传"),Uploading("0","正在上传中"),UploadFinish("1","上传完成");

        String value;
        String desc;

        UploadStatus(String value,String desc){
            this.value = value;
            this.desc = desc;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }
}
