import model.Const;
import model.FileAttributes;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
public class test {
    private static String ROOTPATH = "e:\\test";
    private static String UPLOAD_ROOTPATH = "/apps/krdsgtest";

    public static void main (String [] arg){
        execute();
    }

    private static void execute(){
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
    private static void sync(Properties prop,List<FileAttributes> nowFilesList, Map<String, FileAttributes> oldFilesMap) {
        for(FileAttributes fileAttributes :nowFilesList){
            //正在上传中的文件不管
            if(Const.UploadStatus.Uploading.toString().equals(fileAttributes.getUploadStatus())){
                continue;
            }

            File file = new File(fileAttributes.getName());

            FileAttributes propFileAttributes = oldFilesMap.get(fileAttributes.getName());
            if(propFileAttributes == null){
                //上传
                System.out.println("文件" + fileAttributes.getName() + "需要上传");
                upload(prop, file, fileAttributes);
            }else {
                if(!(fileAttributes.getLastModified()).equals(propFileAttributes.getLastModified())){
                    //更新
                    System.out.println("文件" + propFileAttributes.getName() + "需要更新");
                    //上传文件
                    upload(prop, file, fileAttributes);
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
            delete(prop,oldFilesMap.get(fileName));
        }
    }

    //扫描前一次文件情况
    private static void queryOldFiles(Properties prop,Map<String, FileAttributes> oldFilesMap, String propfileName) {
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
    public static void scanNowFiles(List<FileAttributes> filelist,String strPath) {
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

    private static void saveProperties(Properties prop) {
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
    private static void upload(Properties properties,File file,FileAttributes fileAttributes) {
        updateFileUploadStatus(file, Const.UploadStatus.Uploading.toString());
        String uploadUrl = "https://c.pcs.baidu.com/rest/2.0/pcs/file?method=upload&ondup=overwrite&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498&path=#path#";
        String fileName = fileAttributes.getName();
        fileName = fileName.substring(ROOTPATH.length()).replaceAll("\\\\","/");
        try{
            uploadUrl = uploadUrl.replace("#path#",URLEncoder.encode(UPLOAD_ROOTPATH + fileName, "UTF-8"));
        }catch (Exception e){
            e.printStackTrace();
        }
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
                fileAttributes.setNetDiskType("0");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            postMethod.releaseConnection();
        }

        try{
            updateFileUploadStatus(file,Const.UploadStatus.UploadFinish.toString());
            fileAttributes.setLastModified(file.lastModified());
            String fileAttrs = new ObjectMapper().writeValueAsString(fileAttributes);
            properties.setProperty(fileAttributes.getName(),fileAttrs);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void delete(Properties properties,FileAttributes fileAttributes) {
        String deleteUrl = "https://c.pcs.baidu.com/rest/2.0/pcs/file?method=delete&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498&path=#path#";
        String fileName = fileAttributes.getName();
        fileName = fileName.substring(ROOTPATH.length()).replaceAll("\\\\","/");
        try{
            deleteUrl = deleteUrl.replace("#path#",URLEncoder.encode(UPLOAD_ROOTPATH + fileName, "UTF-8"));
        }catch (Exception e){
            e.printStackTrace();
        }
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
    private static void updateFileUploadStatus(File file,String uploadStatus) {
        UserDefinedFileAttributeView userDefinedFileAttributeView = Files.getFileAttributeView(file.toPath(),
                UserDefinedFileAttributeView.class);
        try {
            userDefinedFileAttributeView.write("uploadStatus", Charset.defaultCharset().encode(uploadStatus));
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}