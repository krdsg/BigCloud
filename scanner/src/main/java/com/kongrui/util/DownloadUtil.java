package com.kongrui.util;

import com.kongrui.model.Const;
import com.kongrui.model.FileAttributes;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.*;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-28
 * Time: 上午12:18
 * To change this template use File | Settings | File Templates.
 */
public class DownloadUtil {
    private static Logger logger = Logger.getLogger(com.kongrui.util.DownloadUtil.class);
    private static ConcurrentHashMap<String,ConcurrentHashMap<String,String>> diskUrlMap = new ConcurrentHashMap<String,ConcurrentHashMap<String,String>>();

    public static void initDownloadAllFiles(Properties properties) {
        String queryUrl = "http://victorjin.duapp.com/query.php?a=all";
        HttpClient httpClient = new HttpClient();
        GetMethod getMethod = new GetMethod(queryUrl);
        try {
            httpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() == HttpStatus.SC_OK) {
                String rtnJsonStr = getMethod.getResponseBodyAsString();
                JSONArray jsonArray = JSONArray.fromObject(rtnJsonStr);
                for(int i = 0;i<jsonArray.size();i++){
                    JSONObject jsonObject = jsonArray.getJSONObject(i);

                    JSONObject listJsonObject = (JSONObject)jsonObject.get("list");
                    String netDiskType = listJsonObject.get("id").toString();

                    ConcurrentHashMap<String,String> urlMap = new ConcurrentHashMap<String, String>();
                    urlMap.put("listUrl",listJsonObject.get("url").toString());
                    urlMap.put("protocol",listJsonObject.get("protocol").toString());
                    urlMap.put("listParam",listJsonObject.get("param").toString());

                    JSONObject downloadJsonObject = (JSONObject)jsonObject.get("download");
                    urlMap.put("downloadUrl",downloadJsonObject.get("url").toString());
                    urlMap.put("downloadParam",downloadJsonObject.get("param").toString());
                    if(!"ftp".equals(urlMap.get("protocol"))){
                        urlMap.put("rootPath",downloadJsonObject.get("root").toString());
                    }

                    diskUrlMap.put(netDiskType,urlMap);

                    if("https".equals(urlMap.get("protocol"))){
                        String rootPath = urlMap.get("rootPath");
                        httpDownloadDir(properties, rootPath, netDiskType);
                    }else if("ftp".equals(urlMap.get("protocol"))){
                        ftpDownload(properties,netDiskType);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }
    }

    private static void httpDownloadDir(Properties properties,String queryPath,String netDiskType){
        File dir = new File(Const.ROOTPATH + queryPath.substring(diskUrlMap.get(netDiskType).get("rootPath").length()).replaceAll("/", "\\\\"));
        if(!dir.exists()){
            dir.mkdir();
        }

        if(queryPath.endsWith("/")){
            queryPath = queryPath.substring(0,queryPath.lastIndexOf("/"));
        }

        HttpClient httpClient = new HttpClient();
        String listUrl = diskUrlMap.get(netDiskType).get("listUrl");

        try {
            listUrl = listUrl.replace("#path#",URLEncoder.encode(queryPath,"UTF-8"));
        }catch (Exception e){
            e.printStackTrace();
        }

        if(listUrl.endsWith("/")){
            listUrl = listUrl.substring(0,listUrl.lastIndexOf("/"));
        }

        listUrl += "?" + diskUrlMap.get(netDiskType).get("listParam");

        try {
            listUrl = listUrl.replace("#path#",URLEncoder.encode(queryPath,"UTF-8"));
        }catch (Exception e){
            e.printStackTrace();
        }

        GetMethod getMethod = new GetMethod(listUrl);
        try {
            httpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() == HttpStatus.SC_OK) {
                String rtnJsonStr = getMethod.getResponseBodyAsString();
                if("0".equals(netDiskType)){
                    JSONArray listJsonArray = (JSONArray)JSONObject.fromObject(rtnJsonStr).get("list");
                    for(int i = 0;i<listJsonArray.size();i++){
                        JSONObject jsonObject = (JSONObject)listJsonArray.get(i);
                        String isDir  = jsonObject.get("isdir").toString();
                        String needDownloadPath = jsonObject.get("path").toString();
                        if("1".equals(isDir)){
                            httpDownloadDir(properties, needDownloadPath, netDiskType);
                        }else if("0".equals(isDir)){
                            httpDownloadFile(properties, needDownloadPath, netDiskType);
                        }
                    }
                }else if("2".equals(netDiskType)){
                    JSONArray listJsonArray = (JSONArray)JSONObject.fromObject(rtnJsonStr).get("contents");
                    for(int i = 0;i<listJsonArray.size();i++){
                        JSONObject jsonObject = listJsonArray.getJSONObject(i);
                        Boolean isDir = (Boolean)jsonObject.get("is_dir");
                        String needDownloadPath = jsonObject.get("path").toString();
                        if(isDir){
                            httpDownloadDir(properties, needDownloadPath, netDiskType);
                        }else{
                            httpDownloadFile(properties, needDownloadPath, netDiskType);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }
    }

    private static void httpDownloadFile(Properties properties,String needDownloadPath, String netDiskType) {
        HttpClient httpClient = new HttpClient();
        String downloadUrl = diskUrlMap.get(netDiskType).get("downloadUrl") + "?";
//        String downloadUrl = "https://pcs.baidu.com/rest/2.0/pcs/file?method=download&path=%2Fapps%2Fkrdsgtest%2FHydrangeas.jpg&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498";
//        String downloadUrl = "https://api.weipan.cn/2/files/sandbox/aaa/ddd.txt?&access_token=5636216662nedmw1cyF3n29onGk21605";
        try {
            downloadUrl += diskUrlMap.get(netDiskType).get("downloadParam").replace("#path#",URLEncoder.encode(needDownloadPath,"utf-8"));
        }catch (Exception e){
            e.printStackTrace();
        }

        GetMethod getMethod = new GetMethod(downloadUrl);
        try {
            httpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() == HttpStatus.SC_OK) {
                InputStream in = getMethod.getResponseBodyAsStream();
                String rootPath = diskUrlMap.get(netDiskType).get("rootPath");
                String relationPath = "\\\\" + needDownloadPath.substring(rootPath.length()).replaceAll("/","\\\\");
                File localPath = null;
                String dir = Const.ROOTPATH;
                String[] paths = relationPath.split("\\\\");
                for(int i=0;i<paths.length;i++){
                    dir += "\\\\" + paths[i];
                    localPath = new File(dir);
                    if(i != paths.length - 1){
                        if(!localPath.exists()){
                            localPath.mkdir();
                        }
                    }
                }
                FileOutputStream out = new FileOutputStream(localPath);
                byte[] b = new byte[1024];
                int len = 0;
                while((len=in.read(b))!= -1){
                    out.write(b,0,len);
                }
                in.close();
                out.close();

                FileAttributes fileAttributes = new FileAttributes();
                fileAttributes.setNetDiskType(netDiskType);
                fileAttributes.setUploadStatus("1");
                fileAttributes.setName(localPath.getAbsolutePath());
                fileAttributes.setNetDiskPath(needDownloadPath);
                File currentFile = new File(localPath.getAbsolutePath());
                fileAttributes.setLastModified(currentFile.lastModified());
                fileAttributes.setSize(currentFile.length());

                UploadUtil.updateFileUploadStatus(localPath,Const.UploadStatus.UploadFinish.toString());

                try{
                    String fileAttrs = new ObjectMapper().writeValueAsString(fileAttributes);
                    properties.setProperty(fileAttributes.getName(),fileAttrs);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }
    }

    public static void main(String[] arg){
//        httpDownloadFile(new Properties(),"/aaa/ddd.txt","");
//        ftpDownload(new Properties(),"1");
//        System.out.println(Const.ROOTPATH + "\\" + "/apps/krdsgtest/Hydrangeas.jpg".substring("/apps/krdsgtest".length() + 1));

    }

    private static void ftpDownload(Properties properties,String netDiskType){
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
            ftpDownLoadDirectory(ftp,Const.ROOTPATH + "\\",Const.FTP_ROOTPATH,netDiskType,properties);
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


    /***
     * @下载文件夹
     * @param localDirectoryPath 本地地址
     * @param remoteDirectory
     *            远程文件夹
     * */
    public static boolean ftpDownLoadDirectory(FTPClient ftpClient,String localDirectoryPath,
                                     String remoteDirectory,String netDiskType,Properties properties) {
        try {
            String fileName = new File(remoteDirectory).getName();
            localDirectoryPath = localDirectoryPath + fileName + "\\";
            File localDirectoryDir = new File(localDirectoryPath);
            if(!localDirectoryDir.exists()){
                localDirectoryDir.mkdirs();
            }
            FTPFile[] allFile = ftpClient.listFiles(remoteDirectory);
            for (int currentFile = 0; currentFile < allFile.length; currentFile++) {
                if (allFile[currentFile].isDirectory()) {
                    String strremoteDirectoryPath = remoteDirectory + "\\"
                            + allFile[currentFile].getName();
                    ftpDownLoadDirectory(ftpClient, new String(localDirectoryPath.getBytes("ISO-8859-1"),"UTF-8"),
                            new String(strremoteDirectoryPath.getBytes("ISO-8859-1"),"UTF-8"), netDiskType, properties);
                }
            }

            for (int currentFile = 0; currentFile < allFile.length; currentFile++) {
                if (!allFile[currentFile].isDirectory()) {
                    ftpDownloadFile(ftpClient, new String((allFile[currentFile].getName()).getBytes("ISO-8859-1"),"UTF-8"),
                            new String(localDirectoryPath.getBytes("ISO-8859-1"),"UTF-8"),
                            new String(remoteDirectory.getBytes("ISO-8859-1"),"UTF-8"), netDiskType, properties);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.info("下载文件夹失败");
            return false;
        }
        return true;
    }

    /***
     * 下载文件
     *
     * @param remoteFileName
     *            待下载文件名称
     * @param localDires
     *            下载到当地那个路径下
     * @param remoteDownLoadPath
     *            remoteFileName所在的路径
     * */

    public static boolean ftpDownloadFile(FTPClient ftpClient,String remoteFileName, String localDires,
                                String remoteDownLoadPath,String netDiskType,Properties properties) {
        String strFilePath = localDires + remoteFileName;
        BufferedOutputStream outStream = null;
        boolean success = false;
        try {
            ftpClient.changeWorkingDirectory(remoteDownLoadPath.replaceAll("\\\\","/"));
            outStream = new BufferedOutputStream(new FileOutputStream(
                    strFilePath));
            logger.info(remoteFileName + "开始下载....");
            success = ftpClient.retrieveFile(remoteFileName, outStream);
            if (success == true) {
                logger.info(remoteFileName + "成功下载到" + strFilePath);

                FileAttributes fileAttributes = new FileAttributes();
                fileAttributes.setNetDiskType(netDiskType);
                fileAttributes.setUploadStatus("1");
                fileAttributes.setName(strFilePath);
                fileAttributes.setNetDiskPath(remoteDownLoadPath);
                File currentFile = new File(strFilePath);
                fileAttributes.setLastModified(currentFile.lastModified());
                fileAttributes.setSize(currentFile.length());

                try{
                    String fileAttrs = new ObjectMapper().writeValueAsString(fileAttributes);
                    properties.setProperty(fileAttributes.getName(),fileAttrs);
                }catch (Exception e){
                    e.printStackTrace();
                }

                UploadUtil.updateFileUploadStatus(new File(strFilePath),Const.UploadStatus.UploadFinish.toString());

                return success;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(remoteFileName + "下载失败");
        } finally {
            if (null != outStream) {
                try {
                    outStream.flush();
                    outStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (success == false) {
            logger.error(remoteFileName + "下载失败!!!");
        }
        return success;
    }
}
