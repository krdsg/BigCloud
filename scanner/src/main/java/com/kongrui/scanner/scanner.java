package com.kongrui.scanner;

import com.kongrui.model.Const;
import com.kongrui.model.FileAttributes;
import com.kongrui.util.DeleteUtil;
import com.kongrui.util.DownloadUtil;
import com.kongrui.util.UploadUtil;
import org.apache.commons.lang.time.DateUtils;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.*;
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

    public static void main (String [] arg){
        File file = new File(Const.ROOTPATH + "\\desktop.ini");
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
        Set<FileAttributes> nowfileslist = new HashSet<FileAttributes>();
        scanNowFiles(nowfileslist,Const.ROOTPATH);
        //获取前一次记录的文件情况
        Properties prop = new Properties();// 属性集合对象
        Map<String,FileAttributes> oldFilesMap = new HashMap<String, FileAttributes>();

        File fileProperties = new File(Const.PROPERTIES_FILENAME);
        if(!fileProperties.exists()){
            try {
                fileProperties.createNewFile();
            } catch (Exception e){
                e.printStackTrace();
            }

            if(nowfileslist.isEmpty()){
                DownloadUtil.initDownloadAllFiles(prop);
                saveProperties(prop);
                return;
            }
        }

        scanNowFiles(nowfileslist,Const.ROOTPATH);
        queryOldFiles(prop,oldFilesMap,Const.PROPERTIES_FILENAME);

        sync(prop, nowfileslist, oldFilesMap);

        saveProperties(prop);
    }

    //对比处理
    private void sync(Properties prop,Set<FileAttributes> nowFilesList, Map<String, FileAttributes> oldFilesMap) {
        for(FileAttributes fileAttributes :nowFilesList){
            if(fileAttributes.getName() != null && fileAttributes.getName().contains("\\desktop.ini")){
                continue;
            }
            //正在上传中且上次修改时间不超过3分钟的的文件不管
            if(Const.UploadStatus.Uploading.toString().equals(fileAttributes.getUploadStatus()) && DateUtils.addMinutes(new Date(),-3).getTime() < fileAttributes.getLastModified()){
                continue;
            }

            File file = new File(fileAttributes.getName());

            FileAttributes propFileAttributes = oldFilesMap.get(fileAttributes.getName());
            if(propFileAttributes == null || propFileAttributes.getNetDiskType() == null){
                //上传
                System.out.println("文件" + fileAttributes.getName() + "需要上传");
                UploadUtil.upload(prop, file, fileAttributes, "-1");
            }else {
                if(!(fileAttributes.getLastModified()).equals(propFileAttributes.getLastModified())){
                    //更新
                    System.out.println("文件" + propFileAttributes.getName() + "需要更新");
                    //上传文件
                    UploadUtil.upload(prop, file, fileAttributes, propFileAttributes.getNetDiskType());
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
            DeleteUtil.delete(prop, oldFilesMap.get(fileName), oldFilesMap.get(fileName).getNetDiskType());
        }

        updateFileUploadStatus(Const.ROOTPATH);
    }

    private void updateFileUploadStatus(String path) {
        Boolean updateFinished = true;
        File dir = new File(path);
        File[] files = dir.listFiles();
        for(File file:files){
            if(file.isDirectory()){
                updateFileUploadStatus(file.getAbsolutePath());
            }else if(!file.getAbsolutePath().contains("\\desktop.ini")){
                String strFileName = file.getAbsolutePath().toLowerCase();

                UserDefinedFileAttributeView userDefinedFileAttributeView = Files.getFileAttributeView(file.toPath(),
                        UserDefinedFileAttributeView.class);
                try {
                    int size = userDefinedFileAttributeView.size("uploadStatus");
                    ByteBuffer bb = ByteBuffer.allocateDirect(size);
                    userDefinedFileAttributeView.read("uploadStatus", bb);
                    bb.flip();
                    if(Const.UploadStatus.Uploading.toString().equals(Charset.defaultCharset().decode(bb).toString())){
                        updateFinished = false;
                        break;
                    }
                }catch (Exception e) {
                    updateFinished = false;
                    e.printStackTrace();
                }
            }
        }

        try {
            String dirPath = dir.getAbsolutePath();
            if(dirPath.endsWith("\\")){
                dirPath = dirPath.substring(0,dirPath.lastIndexOf("\\"));
            }

            if(!updateFinished){
                Icon.changeIcon(Icon.UPDATING, dirPath);
            }else{
                Icon.changeIcon(Icon.UPDATE_SUCCESS, dirPath);
            }
        }catch (Exception e){
            e.printStackTrace();
        }


    }

    //扫描前一次文件情况
    private void queryOldFiles(Properties prop,Map<String, FileAttributes> oldFilesMap, String propfileName) {
        try{
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
    public void scanNowFiles(Set<FileAttributes> filelist,String strPath) {
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
            FileOutputStream fos = new FileOutputStream(Const.PROPERTIES_FILENAME);
            // 将Properties集合保存到流中
            prop.store(fos, "ohyeah");
            fos.close();// 关闭流
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}