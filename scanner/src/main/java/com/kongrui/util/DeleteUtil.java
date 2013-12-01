package com.kongrui.util;

import com.kongrui.model.Const;
import com.kongrui.model.FileAttributes;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-28
 * Time: 上午12:08
 * To change this template use File | Settings | File Templates.
 */
public class DeleteUtil {

    public static void delete(Properties properties,FileAttributes fileAttributes,String netDiskType){
        String queryUrl = "http://victorjin.duapp.com/query.php?a=delete&f=#fileName#";
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
                    httpDelete(properties, fileAttributes, uploadUrl + "?" + param);
                }else if("ftp".equals(protocol)){
                    ftpDelete(properties, fileAttributes, uploadUrl);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }
    }

    private static void ftpDelete(Properties properties, FileAttributes fileAttributes, String deleteUrl) {
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
            String remoteFilePath = Const.FTP_ROOTPATH + fileAttributes.getName().substring(Const.ROOTPATH.length(),fileAttributes.getName().lastIndexOf("\\")).replaceAll("\\\\","/") + "/";
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

    private static void httpDelete(Properties properties,FileAttributes fileAttributes,String deleteUrl) {
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
}
