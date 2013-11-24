package com.kongrui.scanner;

import com.kongrui.model.Const;
import com.kongrui.model.FileAttributes;
import com.kongrui.model.FtpUtil;
import javafx.util.converter.DateStringConverter;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.net.ftp.FTPReply;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-4
 * Time: 下午11:09
 * To change this template use File | Settings | File Templates.
 */
public class Scanner {
    private static String ROOTPATH = "e:\\test";
    private static String UPLOAD_ROOTPATH = "/apps/krdsgtest";
    private static String FTP_ROOTPATH = "/bigCloud";

    public static void main (String [] arg){
        File file = new File(ROOTPATH + "\\desktop.ini");
//        File file = new File(ROOTPATH + "\\11.txt");
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file.getPath());
            String str = "sdfasdfasdfasdfasdf";
            fileWriter.write(str);
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

        }

    }

    public void execute(){
        //扫描当前文件情况
        List<FileAttributes> nowfileslist = new ArrayList<FileAttributes>();
        scanNowFiles(nowfileslist,ROOTPATH);
        //获取前一次记录的文件情况
        Properties prop = new Properties();// 属性集合对象
        Map<String,FileAttributes> oldFilesMap = new HashMap<String, FileAttributes>();
        queryOldFiles(prop,oldFilesMap,"prop.properties");

        sync(prop, nowfileslist, oldFilesMap);

        saveProperties(prop);
    }

    //对比处理
    private void sync(Properties prop,List<FileAttributes> nowFilesList, Map<String, FileAttributes> oldFilesMap) {
        for(FileAttributes fileAttributes :nowFilesList){
            if((ROOTPATH + "\\desktop.ini").equals(fileAttributes.getName())){
                continue;
            }
            //正在上传中且上次修改时间不超过3分钟的的文件不管
            if(Const.UploadStatus.Uploading.toString().equals(fileAttributes.getUploadStatus()) && DateUtils.addMinutes(new Date(),-3).getTime() < fileAttributes.getLastModified()){
                continue;
            }

            File file = new File(fileAttributes.getName());

            FileAttributes propFileAttributes = oldFilesMap.get(fileAttributes.getName());
            if(propFileAttributes == null || propFileAttributes.getNetDiskType() == null || "-1".equals(propFileAttributes.getNetDiskType())){
                //上传
                System.out.println("文件" + fileAttributes.getName() + "需要上传");
                upload(prop, file, fileAttributes,"-1");
            }else {
                if(!(fileAttributes.getLastModified()).equals(propFileAttributes.getLastModified())){
                    //更新
                    System.out.println("文件" + propFileAttributes.getName() + "需要更新");
                    //上传文件
                    upload(prop, file, fileAttributes, propFileAttributes.getNetDiskType());
                }
            }
        }

        //是否有删除文件
        for(FileAttributes fileAttributes: nowFilesList){
            oldFilesMap.remove(fileAttributes.getName());
        }
        Set<String> needDeleteFileNameSet = oldFilesMap.keySet();
        for(String fileName:needDeleteFileNameSet){
            //删除
            System.out.println("文件" + oldFilesMap.get(fileName).getName() + "需要删除");
            //delete
            delete(prop,oldFilesMap.get(fileName),oldFilesMap.get(fileName).getNetDiskType());
        }
    }

    //扫描前一次文件情况
    private void queryOldFiles(Properties prop,Map<String, FileAttributes> oldFilesMap, String propfileName) {
        File fileProperties = new File(propfileName);
        try{
            if(!fileProperties.exists()){
                fileProperties.createNewFile();
            }
            FileInputStream fis = new FileInputStream(propfileName);// 属性文件输入流
            prop.load(fis);// 将属性文件流装载到Properties对象中
            Set<String> fileNameSet = prop.stringPropertyNames();
            for(String fileName:fileNameSet){
                FileAttributes fileAttributes = new ObjectMapper().readValue((String)prop.get(fileName),FileAttributes.class);
                oldFilesMap.put(fileName,fileAttributes);
            }
            fis.close();// 关闭流
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //扫描当前文件
    public void scanNowFiles(List<FileAttributes> filelist,String strPath) {
        File dir = new File(strPath);
        File[] files = dir.listFiles();

        if (files == null)
            return;
        for (File file:files) {
            if (file.isDirectory()) {
                scanNowFiles(filelist, file.getAbsolutePath());
            } else {
                String strFileName = file.getAbsolutePath().toLowerCase();
                System.out.println("---"+strFileName);
                FileAttributes fileAttributes = new FileAttributes();
                fileAttributes.setName(file.getAbsolutePath());
                fileAttributes.setLastModified(file.lastModified());
                fileAttributes.setSize(file.length());

                UserDefinedFileAttributeView userDefinedFileAttributeView = Files.getFileAttributeView(file.toPath(),
                        UserDefinedFileAttributeView.class);
                try {
                    int size = userDefinedFileAttributeView.size("uploadStatus");
                    ByteBuffer bb = ByteBuffer.allocateDirect(size);
                    userDefinedFileAttributeView.read("uploadStatus", bb);
                    bb.flip();
                    fileAttributes.setUploadStatus(Charset.defaultCharset().decode(bb).toString());
                }catch (NoSuchFileException e){
                    fileAttributes.setUploadStatus(Const.UploadStatus.WaitToUpload.toString());
                }catch (IOException e) {
                    e.printStackTrace();
                }

                filelist.add(fileAttributes);
            }
        }
    }

    private void saveProperties(Properties prop) {
        try {
            // 文件输出流
            FileOutputStream fos = new FileOutputStream("prop.properties");
            // 将Properties集合保存到流中
            prop.store(fos, "ohyeah");
            fos.close();// 关闭流
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    //上传文件
    @Async
    private void upload(Properties properties,File file,FileAttributes fileAttributes,String netDiskType) {
//        System.out.println(file.getAbsolutePath() + "开始");
        updateFileUploadStatus(file, Const.UploadStatus.Uploading.toString());
        String queryUrl = "http://victorjin.duapp.com/query.php?f=#fileName#";
        try{
            String fileName = fileAttributes.getName();
            fileName = fileName.substring(ROOTPATH.length() + 1).replaceAll("\\\\","/");
            queryUrl = queryUrl.replace("#fileName#",URLEncoder.encode(fileName, "UTF-8"));
            if(netDiskType != null && !"-1".equals(netDiskType)){
                queryUrl += "n=" + netDiskType;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        HttpClient httpClient = new HttpClient();
        GetMethod getMethod = new GetMethod(queryUrl);
        try {
            httpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() == HttpStatus.SC_OK) {
                String rtnJsonStr = getMethod.getResponseBodyAsString();
                String uploadUrl = JSONObject.fromObject(rtnJsonStr).getString("url");
                String protocol = JSONObject.fromObject(rtnJsonStr).getString("protocol");
                String id = JSONObject.fromObject(rtnJsonStr).getString("id");
                String param = JSONObject.fromObject(rtnJsonStr).getString("param");
                /*if("https".equals(protocol)){
                    httpUpload(properties,file,fileAttributes,uploadUrl + "?" + param,id);
                }else if("ftp".equals(protocol)){
                    ftpUpload(properties,file,fileAttributes,uploadUrl,id);
                }*/
                //httpUpload(properties,file,fileAttributes,uploadUrl + "?" + param,id);
                ftpUpload(properties,file,fileAttributes,uploadUrl,id);

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }

        try{
            updateFileUploadStatus(file,Const.UploadStatus.UploadFinish.toString());
            fileAttributes.setUploadStatus(Const.UploadStatus.UploadFinish.toString());
            fileAttributes.setLastModified(file.lastModified());
            String fileAttrs = new ObjectMapper().writeValueAsString(fileAttributes);
            properties.setProperty(fileAttributes.getName(),fileAttrs);
        }catch (Exception e){
            e.printStackTrace();
        }
//        System.out.println(file.getAbsolutePath() + "结束");
    }

    private void httpUpload(Properties properties,File file,FileAttributes fileAttributes,String uploadUrl,String netDiskType){
//        String uploadUrl = "https://c.pcs.baidu.com/rest/2.0/pcs/file?method=upload&ondup=overwrite&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498&path=#path#";

        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(uploadUrl);
        try {
            if(!file.exists()){
                return;
            }
            //post提交的参数
            Part[] parts = {new FilePart(file.getName(),file)};
            //设置多媒体参数，作用类似form表单中的enctype="multipart/form-data"
            postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));
            httpClient.executeMethod(postMethod);
            if (postMethod.getStatusCode() == HttpStatus.SC_OK) {
                String rtnJsonStr = postMethod.getResponseBodyAsString();
                String netDiskPath = JSONObject.fromObject(rtnJsonStr).getString("path");
                fileAttributes.setNetDiskPath(netDiskPath);
                fileAttributes.setNetDiskType(netDiskType);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            postMethod.releaseConnection();
        }
    }

    private void ftpUpload(Properties properties,File file,FileAttributes fileAttributes,String uploadUrl,String netDiskType){
//        ftp://xukh021:sh000000@ftp.ctdisk.com/我的文件夹/"=
        FTPClient ftp = new FTPClient();
        try {
            FileInputStream in = new FileInputStream(file);
            int reply;
            ftp.connect("ftp.ctdisk.com");//连接FTP服务器
            //如果采用默认端口，可以使用ftp.connect(url)的方式直接连接FTP服务器
            ftp.login("xukh021", "sh000000");//登录
            reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
            }
            String remoteFilePath = FTP_ROOTPATH + file.getPath().substring(ROOTPATH.length(),file.getPath().lastIndexOf("\\")).replaceAll("\\\\","/") + "/";
            String paths[] = remoteFilePath.split("/");
            String remotePath = "/";
            for(String path:paths){
                remotePath += path + "/";
                if(!ftp.changeWorkingDirectory(remotePath)){
                    ftp.makeDirectory(remotePath);
                }
            }
            ftp.changeWorkingDirectory(remoteFilePath);
            String fileName = new String((file.getName()).getBytes(),"ISO-8859-1");
            fileAttributes.setNetDiskPath(remoteFilePath + fileName);
            if(ftp.storeFile(fileName, in)){
                fileAttributes.setNetDiskType(netDiskType);
            }

            in.close();
            ftp.logout();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ioe) {
                }
            }
        }

    }

    @Async
    private void delete(Properties properties,FileAttributes fileAttributes,String netDiskType){
        String queryUrl = "http://victorjin.duapp.com/query.php?f=#fileName#";
        try{
            String fileName = fileAttributes.getName();
            fileName = fileName.substring(ROOTPATH.length() + 1).replaceAll("\\\\","/");
            queryUrl = queryUrl.replace("#fileName#",URLEncoder.encode(fileName, "UTF-8"));
            if(netDiskType != null && !"-1".equals(netDiskType)){
                queryUrl += "&n=" + netDiskType;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        HttpClient httpClient = new HttpClient();
        GetMethod getMethod = new GetMethod(queryUrl);
        try {
            httpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() == HttpStatus.SC_OK) {
                String rtnJsonStr = getMethod.getResponseBodyAsString();
                String uploadUrl = JSONObject.fromObject(rtnJsonStr).getString("url");
                String protocol = JSONObject.fromObject(rtnJsonStr).getString("protocol");
                String id = JSONObject.fromObject(rtnJsonStr).getString("id");
                String param = JSONObject.fromObject(rtnJsonStr).getString("param");
                if("https".equals(protocol)){
                    httpDelete(properties, fileAttributes, uploadUrl + "?" + param);
                }else if("ftp".equals(protocol)){
                    ftpDelete(properties, fileAttributes, uploadUrl);
                }
                //httpUpload(properties,file,fileAttributes,uploadUrl + "?" + param,id);
                //ftpUpload(properties,file,fileAttributes,uploadUrl,id);

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }
    }

    private void ftpDelete(Properties properties, FileAttributes fileAttributes, String deleteUrl) {
        //        ftp://xukh021:sh000000@ftp.ctdisk.com/我的文件夹/"
        FTPClient ftp = new FTPClient();
        try {
            int reply;
            ftp.connect("ftp.ctdisk.com");//连接FTP服务器
            //如果采用默认端口，可以使用ftp.connect(url)的方式直接连接FTP服务器
            ftp.login("xukh021", "sh000000");//登录
            reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
            }
            String remoteFilePath = FTP_ROOTPATH + fileAttributes.getName().substring(ROOTPATH.length(),fileAttributes.getName().lastIndexOf("\\")).replaceAll("\\\\","/") + "/";
            ftp.changeWorkingDirectory(remoteFilePath);
            String fileName = fileAttributes.getName().substring(fileAttributes.getName().lastIndexOf("\\") + 1);
            fileName = new String(fileName.getBytes(),"ISO-8859-1");
            if(ftp.deleteFile(fileName)){
                properties.remove(fileAttributes.getName());
            }

            ftp.logout();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ftp.isConnected()) {
                try {
                    ftp.disconnect();
                } catch (IOException ioe) {
                }
            }
        }
    }

    private void httpDelete(Properties properties,FileAttributes fileAttributes,String deleteUrl) {
//        String deleteUrl = "https://c.pcs.baidu.com/rest/2.0/pcs/file?method=delete&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498&path=#path#";
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(deleteUrl);
        try {
            httpClient.executeMethod(postMethod);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            postMethod.releaseConnection();
        }

        properties.remove(fileAttributes.getName());
    }

    /**
     * 设置文件上传状态
     * @param file 具体文件
     * @param uploadStatus 上传状态
     */
    private void updateFileUploadStatus(File file,String uploadStatus) {
        try{
            if(Const.UploadStatus.Uploading.toString().equals(uploadStatus)){
                Icon.changeIcon(Icon.UPDATING, file.getPath().substring(0,file.getPath().lastIndexOf("\\")));
            }else if(Const.UploadStatus.UploadFinish.toString().equals(uploadStatus)){
                Icon.changeIcon(Icon.UPDATE_SUCCESS, file.getPath().substring(0,file.getPath().lastIndexOf("\\")));
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        UserDefinedFileAttributeView userDefinedFileAttributeView = Files.getFileAttributeView(file.toPath(),
                UserDefinedFileAttributeView.class);
        try {
            userDefinedFileAttributeView.write("uploadStatus", Charset.defaultCharset().encode(uploadStatus));
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}