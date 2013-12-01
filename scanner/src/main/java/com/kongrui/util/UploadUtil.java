package com.kongrui.util;

import com.kongrui.model.Const;
import com.kongrui.model.FileAttributes;
import com.kongrui.scanner.Icon;
import http.InstallCert;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.codehaus.jackson.map.ObjectMapper;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-28
 * Time: 上午12:02
 * To change this template use File | Settings | File Templates.
 */
public class UploadUtil {

    //上传文件
    public static void upload(Properties properties,File file,FileAttributes fileAttributes,String netDiskType) {
//        System.out.println(file.getAbsolutePath() + "开始");
        updateFileUploadStatus(file, Const.UploadStatus.Uploading.toString());
        String queryUrl = "http://victorjin.duapp.com/query.php?a=upload&f=#fileName#";
        try{
            String fileName = fileAttributes.getName();
            fileName = fileName.substring(Const.ROOTPATH.length() + 1).replaceAll("\\\\","/");
            queryUrl = queryUrl.replace("#fileName#", URLEncoder.encode(fileName, "UTF-8"));
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
                    String method = JSONObject.fromObject(rtnJsonStr).getString("method");
                    if("POST".equalsIgnoreCase(method)){
                        httpUpload(file,fileAttributes,uploadUrl + "?" + param,id);
                    }else if("PUT".equalsIgnoreCase(method)){
                        httpUpload_put(file, fileAttributes, uploadUrl + "?" + param, id);
                    }
                }else if("ftp".equals(protocol)){
                    ftpUpload(file,fileAttributes,uploadUrl,id);
                }
//                httpUpload_put(file, fileAttributes, uploadUrl + "?" + param, id);
                //ftpUpload(properties,file,fileAttributes,uploadUrl,id);

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

    private static void httpUpload(File file,FileAttributes fileAttributes,String uploadUrl,String netDiskType){
//        String uploadUrl = "https://c.pcs.baidu.com/rest/2.0/pcs/file?method=upload&ondup=overwrite&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498&path=#path#";
//        uploadUrl = "https://upload-vdisk.sina.com.cn/2/files/sandbox/<path>";
//        uploadUrl = uploadUrl.replaceAll("<path>",file.getAbsolutePath().substring(Const.ROOTPATH.length() + 1));
//        uploadUrl = "https://api.weipan.cn/2/delta/sandbox?access_token=8b12816662nedmw1cyF3n29onGk81787";
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(uploadUrl);
        try {
            if(!file.exists()){
                return;
            }
            /*NameValuePair[] nameValuePairs = { new NameValuePair("access_token","b4c5fa6662nedmw1cyF3n29onGkfab15")};
            postMethod.setRequestBody(nameValuePairs);
            postMethod.addRequestHeader("Authorization", "OAuth2 b4c5fa6662nedmw1cyF3n29onGkfab15");*/
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

    private static void httpUpload_put(File file,FileAttributes fileAttributes,String uploadUrl,String netDiskType){
        String link = "upload-vdisk.sina.com.cn";
        FileInputStream fs = null;
        try {
            /*uploadUrl = "https://"
                    + link
                    + "/2/files_put/sandbox/" + URLEncoder.encode(file.getAbsolutePath().substring(Const.ROOTPATH.length() + 1).replaceAll("\\\\","/") ,"utf-8")
                    + "?access_token=5636216662nedmw1cyF3n29onGk21605&overwrite=true";*/

            System.setProperty("javax.net.ssl.trustStore",
                    InstallCert.getCertFile(link));
            HttpClient httpClient = new HttpClient();
            PutMethod putMethod = new PutMethod(uploadUrl);
            fs = new FileInputStream(file);
            putMethod.setRequestBody(fs);
            httpClient.executeMethod(putMethod);
            if (putMethod.getStatusCode() == HttpStatus.SC_OK) {
                String rtnJsonStr = putMethod.getResponseBodyAsString();
                String netDiskPath = JSONObject.fromObject(rtnJsonStr).getString("path");
                fileAttributes.setNetDiskPath(netDiskPath);
                fileAttributes.setNetDiskType(netDiskType);
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally{
            try{
                if (fs != null){
                    fs.close();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void ftpUpload(File file,FileAttributes fileAttributes,String uploadUrl,String netDiskType){
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
            String remoteFilePath = Const.FTP_ROOTPATH + file.getPath().substring(Const.ROOTPATH.length(),file.getPath().lastIndexOf("\\")).replaceAll("\\\\","/") + "/";
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

    /**
     * 设置文件上传状态
     * @param file 具体文件
     * @param uploadStatus 上传状态
     */
    public static void updateFileUploadStatus(File file,String uploadStatus) {
        /*try{
            if(Const.UploadStatus.Uploading.toString().equals(uploadStatus)){
                Icon.changeIcon(Icon.UPDATING, file.getPath().substring(0, file.getPath().lastIndexOf("\\")));
            }else if(Const.UploadStatus.UploadFinish.toString().equals(uploadStatus)){
                Icon.changeIcon(Icon.UPDATE_SUCCESS, file.getPath().substring(0,file.getPath().lastIndexOf("\\")));
            }
        }catch (Exception e){
            e.printStackTrace();
        }*/

        UserDefinedFileAttributeView userDefinedFileAttributeView = Files.getFileAttributeView(file.toPath(),
                UserDefinedFileAttributeView.class);
        try {
            userDefinedFileAttributeView.write("uploadStatus", Charset.defaultCharset().encode(uploadStatus));
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}
