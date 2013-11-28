package com.kongrui.util;

import com.kongrui.model.Const;
import com.kongrui.model.FileAttributes;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
                    urlMap.put("rootPath",listJsonObject.get("rootPath").toString());

                    JSONObject downloadJsonObject = (JSONObject)jsonObject.get("download");
                    urlMap.put("downloadUrl",downloadJsonObject.get("url").toString());
                    urlMap.put("downloadParam",downloadJsonObject.get("param").toString());

                    diskUrlMap.put(netDiskType,urlMap);

                    if("https".equals(urlMap.get("protocol"))){
                        String rootPath = urlMap.get("rootPath");
                        File dir = new File(rootPath);
                        if(!dir.exists()){
                            dir.mkdir();
                        }
                        httpDownloadDir(properties, rootPath, netDiskType);
                    }else if("ftp".equals(urlMap.get("protocol"))){
//                        ftpDownload(properties,listUrl,downloadUrl,netDiskType);
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
        File dir = new File(queryPath);
        if(!dir.exists()){
            dir.mkdir();
        }

        HttpClient httpClient = new HttpClient();
        String listUrl = diskUrlMap.get(netDiskType).get("listUrl") + diskUrlMap.get(netDiskType).get("param").replace("#path#",queryPath);

        GetMethod getMethod = new GetMethod(listUrl);
        try {
            httpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() == HttpStatus.SC_OK) {
                String rtnJsonStr = getMethod.getResponseBodyAsString();
                JSONObject listJSONObject = (JSONObject)JSONObject.fromObject(rtnJsonStr).get("list");
                JSONArray listJsonArray = JSONArray.fromObject(listJSONObject);
                for(int i = 0;i<listJsonArray.size();i++){
                    JSONObject jsonObject = listJsonArray.getJSONObject(i);
                    String isDir  = jsonObject.get("isDir").toString();
                    String needDownloadPath = jsonObject.get("path").toString();
                    if("1".equals(isDir)){
                        httpDownloadDir(properties, needDownloadPath, netDiskType);
                    }else if("0".equals(isDir)){
//                        httpDownloadFile(properties, needDownloadPath, netDiskType);
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
        String downloadUrl = diskUrlMap.get(netDiskType).get("downloadUrl") + diskUrlMap.get(netDiskType).get("param").replace("#path#",needDownloadPath);
//        String downloadUrl = "https://pcs.baidu.com/rest/2.0/pcs/file?method=download&path=%2Fapps%2Fkrdsgtest%2FHydrangeas.jpg&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498";
        try {
            URLEncoder.encode(downloadUrl,"utf-8");
        }catch (Exception e){
            e.printStackTrace();
        }

        GetMethod getMethod = new GetMethod(downloadUrl);
        try {
            httpClient.executeMethod(getMethod);
            if (getMethod.getStatusCode() == HttpStatus.SC_OK) {
                InputStream in = getMethod.getResponseBodyAsStream();
                String rootPath = diskUrlMap.get(netDiskType).get("rootPath");
//                String rootPath = "/apps/krdsgtest";
                FileOutputStream out = new FileOutputStream(new File(Const.ROOTPATH + "\\" + needDownloadPath.substring(rootPath.length() + 1)));
                byte[] b = new byte[1024];
                int len = 0;
                while((len=in.read(b))!= -1){
                    out.write(b,0,len);
                }
                in.close();
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            getMethod.releaseConnection();
        }
    }

    public static void main(String[] arg){
//        httpDownloadFile(new Properties(),"/apps/krdsgtest/Jellyfish.jpg","");
//        System.out.println(Const.ROOTPATH + "\\" + "/apps/krdsgtest/Hydrangeas.jpg".substring("/apps/krdsgtest".length() + 1));
    }

    private static void ftpDownload(Properties properties,String listUrl,String uploadUrl,String netDiskType){

    }
}
