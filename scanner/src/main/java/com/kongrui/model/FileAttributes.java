package com.kongrui.model;

/**
 * Created with IntelliJ IDEA.
 * User: dy
 * Date: 13-11-5
 * Time: 下午3:46
 * To change this template use File | Settings | File Templates.
 */
public class FileAttributes {
    private String name;   //文件本地绝对路径
    private String fileId; //网盘id
    private Long lastModified; //上次修改时间
    private String netDiskType; //网盘类型
    private Long size; //文件大小
    private String netDiskPath; //网盘路径
    private String uploadStatus;  //文件上传状态

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public Long getLastModified() {
        return lastModified;
    }

    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    public String getNetDiskType() {
        return netDiskType;
    }

    public void setNetDiskType(String netDiskType) {
        this.netDiskType = netDiskType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getNetDiskPath() {
        return netDiskPath;
    }

    public void setNetDiskPath(String netDiskPath) {
        this.netDiskPath = netDiskPath;
    }

    public String getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(String uploadStatus) {
        this.uploadStatus = uploadStatus;
    }
}