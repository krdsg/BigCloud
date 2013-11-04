import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-4
 * Time: 下午11:09
 * To change this template use File | Settings | File Templates.
 */
public class test {
    public static void main (String [] arg){
        upload();
        scanFile();
    }

    private static void upload() {
        String uploadUrl = "https://c.pcs.baidu.com/rest/2.0/pcs/file?method=upload&path=%2fapps%2fkrdsgtest%2faa%2f11.txt&ondup=overwrite&access_token=3.87a69104e73a85199d56d2bb47ea19cf.2592000.1386170961.1094572425-1647498";
        //获取当前用户空间配额信息
        String getAvailableSpaceUrl = "https://pcs.baidu.com/rest/2.0/pcs/quota?method=info&access_token=3.4c5c0866c55dd98453849b3cc75f6105.2592000.1385982460.1094572425-238347";
//        String getAccessTokenUrl = "https://pcs.baidu.com/rest/2.0/pcs/file?method=upload&path=%2fapps%2falbum%2f1.JPG&access_token=b778fb598c717c0ad7ea8c97c8f3a46f";
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(uploadUrl);
//        GetMethod postMethod = new GetMethod(getAvailableSpaceUrl);
        String flagLock = "";
        try {
            File file = new File("F:\\test\\11.txt");
            //post提交的参数
            Part[] parts = {new FilePart(file.getName(),file)};
            //设置多媒体参数，作用类似form表单中的enctype="multipart/form-data"
            postMethod.setRequestEntity(new MultipartRequestEntity(parts, postMethod.getParams()));
            httpClient.executeMethod(postMethod);
            if (postMethod.getStatusCode() == HttpStatus.SC_OK) {
                flagLock = postMethod.getResponseBodyAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            postMethod.releaseConnection();
        }
    }

    private static List<String> filelist = new ArrayList<String>();

    private static void scanFile(){
        refreshFileList("f:\\test");
        Properties prop = new Properties();// 属性集合对象
        File fileProperties = new File("f:\\test\\prop.properties");

        try{
            if(!fileProperties.exists()){
                fileProperties.createNewFile();
            }
            FileInputStream fis = new FileInputStream("f:\\test\\prop.properties");// 属性文件输入流
            prop.load(fis);// 将属性文件流装载到Properties对象中
            fis.close();// 关闭流
        }catch (Exception e){
            e.printStackTrace();
        }

        //是否有新文件或者文件更新
        for(String filePath:filelist){
            File file = new File(filePath);
            if(prop.get(filePath) == null){
                //上传
                System.out.println("文件" + filePath + "需要上传");
                prop.setProperty(filePath,file.lastModified() + "");
            }else {
                if(!file.exists()){
                    continue;
                }else {
                    if(!(file.lastModified() + "").equals(prop.get(filePath))){
                        //更新
                        System.out.println("文件" + filePath + "需要更新");
                        prop.setProperty(filePath,file.lastModified() + "");
                    }
                }
            }
        }

        //是否有删除文件
        Set<String> fileNameSet = prop.stringPropertyNames();
        fileNameSet.removeAll(filelist);
        for(String fileName:fileNameSet){
            //删除
            System.out.println("文件" + fileName + "需要删除");
            prop.remove(fileName);
        }

        try {
            // 文件输出流
            FileOutputStream fos = new FileOutputStream("f:\\test\\prop.properties");
            // 将Properties集合保存到流中
            prop.store(fos, "ohyeah");
            fos.close();// 关闭流
        } catch (Exception e){
            e.printStackTrace();
        }

    }
    public static void refreshFileList(String strPath) {
        File dir = new File(strPath);
        File[] files = dir.listFiles();

        if (files == null)
            return;
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                refreshFileList(files[i].getAbsolutePath());
            } else {
                String strFileName = files[i].getAbsolutePath().toLowerCase();
                System.out.println("---"+strFileName);
                filelist.add(files[i].getAbsolutePath());
            }
        }
    }
}
