import model.FileAttributes;
import net.sf.json.JSONObject;
import org.apache.commons.collections.bidimap.AbstractDualBidiMap;
import org.apache.commons.collections.map.AbstractHashedMap;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public static void main (String [] arg){
        //upload();
        scanFile();
    }

    private static void upload(Properties properties,File file,FileAttributes fileAttributes) {
        String uploadUrl = "https://c.pcs.baidu.com/rest/2.0/pcs/file?method=upload&path=%2fapps%2fkrdsgtest%2faa%2f11.txt&ondup=overwrite&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498";
        //获取当前用户空间配额信息
        String getAvailableSpaceUrl = "https://pcs.baidu.com/rest/2.0/pcs/quota?method=info&access_token=3.4c5c0866c55dd98453849b3cc75f6105.2592000.1385982460.1094572425-238347";
//        String getAccessTokenUrl = "https://pcs.baidu.com/rest/2.0/pcs/file?method=upload&path=%2fapps%2falbum%2f1.JPG&access_token=b778fb598c717c0ad7ea8c97c8f3a46f";
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(uploadUrl);
//        GetMethod postMethod = new GetMethod(getAvailableSpaceUrl);
        String flagLock = "";
        try {
            //post提交的参数
            Part[] parts = {new FilePart(file.getName(),file)};
            //设置多媒体参数，作用类似form表单中的enctype="multipart/form-data"
            postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));
            httpClient.executeMethod(postMethod);
            if (postMethod.getStatusCode() == HttpStatus.SC_OK) {
                flagLock = postMethod.getResponseBodyAsString();
                String fileId = JSONObject.fromObject(flagLock).getString("fs_id");
                String netDiskPath = JSONObject.fromObject(flagLock).getString("path");
                fileAttributes.setFileId(fileId);
                fileAttributes.setNetDiskPath(netDiskPath);
                UserDefinedFileAttributeView userDefinedFileAttributeView = Files.getFileAttributeView(file.toPath(),
                        UserDefinedFileAttributeView.class);
                userDefinedFileAttributeView.write("fileId", Charset.defaultCharset().encode(fileId));
                fileAttributes.setLastModified(file.lastModified());
                try{
                    String fileAttrs = "";
                    fileAttrs = new ObjectMapper().writeValueAsString(fileAttributes);
                    properties.setProperty(fileAttributes.getFileId(),fileAttrs);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            postMethod.releaseConnection();
        }
    }

    private static void scanFile(){
        List<FileAttributes> filelist = new ArrayList<FileAttributes>();
        refreshFileList(filelist,"e:\\test");

        Properties prop = new Properties();// 属性集合对象
        File fileProperties = new File("e:\\test\\prop.properties");
        Set<String> fileIdSet = prop.stringPropertyNames();
        Map<String,FileAttributes> propFileAttributesMap = new HashMap<String, FileAttributes>();
        for(String fileId:fileIdSet){
            propFileAttributesMap.put(fileId, (FileAttributes) prop.get(fileId));
        }

        try{
            if(!fileProperties.exists()){
                fileProperties.createNewFile();
            }
            FileInputStream fis = new FileInputStream("e:\\test\\prop.properties");// 属性文件输入流
            prop.load(fis);// 将属性文件流装载到Properties对象中
            fis.close();// 关闭流
        }catch (Exception e){
            e.printStackTrace();
        }


        //是否有新文件或者文件更新
        for(FileAttributes fileAttributes :filelist){
            File file = new File(fileAttributes.getName());
            Path path = file.toPath();

            if(StringUtils.isEmpty(fileAttributes.getFileId())){
                //上传
                System.out.println("文件" + fileAttributes.getName() + "需要上传");
                upload(prop, file, fileAttributes);
            }else {
                if(!file.exists()){
                    continue;
                }else {
                    FileAttributes propFileAttributes = propFileAttributesMap.get(fileAttributes.getFileId());
                    if(!(fileAttributes.getLastModified()).equals(propFileAttributes.getLastModified())){
                        if(!fileAttributes.getName().equals(propFileAttributes.getName())){
                            if(fileAttributes.getSize().equals(propFileAttributes.getSize())){
                                //重命名
                                System.out.println("文件" + propFileAttributes.getName() + "需要重命名为:" + fileAttributes.getName());
                            }else {
                                //删除旧文件
                                delete();
                                //上传新文件
                                upload(prop,file,fileAttributes);
                            }
                        }
                        //更新
                        System.out.println("文件" + filePath + "需要更新");
                        //删除旧文件
                        delete();
                        //上传新文件
                        upload(prop, file, fileAttributes);
                    }
                }
            }
        }

        //是否有删除文件
        for(FileAttributes fileAttributes: filelist){
            propFileAttributesMap.remove(fileAttributes.getFileId());
        }
        Set<String> needDeleteFileIdSet = propFileAttributesMap.keySet();
        for(String fileId:needDeleteFileIdSet){
            //删除
            System.out.println("文件" + propFileAttributesMap.get(fileId).getName() + "需要删除");
            //delete
            delete();
            prop.remove(fileId);
        }

        try {
            // 文件输出流
            FileOutputStream fos = new FileOutputStream("e:\\test\\prop.properties");
            // 将Properties集合保存到流中
            prop.store(fos, "ohyeah");
            fos.close();// 关闭流
        } catch (Exception e){
            e.printStackTrace();
        }

    }
    public static void refreshFileList(List<FileAttributes> filelist,String strPath) {
        File dir = new File(strPath);
        File[] files = dir.listFiles();

        if (files == null)
            return;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                refreshFileList(filelist,files[i].getAbsolutePath());
            } else {
                String strFileName = files[i].getAbsolutePath().toLowerCase();
                System.out.println("---"+strFileName);
                FileAttributes fileAttributes = new FileAttributes();
                fileAttributes.setName(files[i].getAbsolutePath());
                fileAttributes.setLastModified(files[i].lastModified());
                fileAttributes.setSize(files[i].length());

                UserDefinedFileAttributeView userDefinedFileAttributeView = Files.getFileAttributeView(files[i].toPath(),
                        UserDefinedFileAttributeView.class);
                try {
                    int size = userDefinedFileAttributeView.size("fileId");
                    if(size == 0){
                        fileAttributes.setFileId("-1");
                    }else {
                        ByteBuffer bb = ByteBuffer.allocateDirect(size);
                        userDefinedFileAttributeView.read("fileId", bb);
                        bb.flip();
                        fileAttributes.setFileId(Charset.defaultCharset().decode(bb).toString());
                    }

                    size = userDefinedFileAttributeView.size("netDiskType");
                    if(size == 0){
                        fileAttributes.setNetDiskType("-1");
                    }else {
                        ByteBuffer bb = ByteBuffer.allocateDirect(size);
                        userDefinedFileAttributeView.read("netDiskType", bb);
                        bb.flip();
                        fileAttributes.setNetDiskType(Charset.defaultCharset().decode(bb).toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                filelist.add(fileAttributes);
            }
        }
    }
}