package com.kongrui.scanner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-25
 * Time: 上午12:24
 * To change this template use File | Settings | File Templates.
 */
public class Icon {
    /**
     * ���³ɹ�
     */
    public static final int UPDATE_SUCCESS = 0;

    /**
     * ���ڸ���
     */
    public static final int UPDATING = 1;

    private static final String DEST_FILE_NAME = "desktop.ini";

    /**
     * �޸��ļ���״̬
     *
     * �����޸ľ����ļ�ͼ�꣬��Ҫ���Ӳ��ٳ���ͼ�꣬������ʱ����
     *
     * @param status
     *            0:SUCESS; 1:UPDATING
     * @param dirPath
     *            �ļ���·��
     * @throws java.io.IOException
     * @throws java.net.URISyntaxException
     */
    public static void changeIcon(int status, String dirPath)
            throws IOException, URISyntaxException {

        String logoPath = new File(Icon.class.getResource("/").toURI())
                .getAbsolutePath() + "\\";
        switch (status) {
            case UPDATE_SUCCESS:
                logoPath += "update_success.bmp";
                break;
            case UPDATING:
                logoPath += "updating.bmp";
                break;
            default:
                throw new IOException(
                        "Please input right status. 0: update success; 1: updating");
        }
        dirPath = dirPath.replaceFirst("\\\\$", "");
        File f = new File(dirPath + "\\" + DEST_FILE_NAME);
        if (f.exists()) {
            f.delete();
            f.createNewFile();
        }
        FileWriter fw = null;
        try {
            fw = new FileWriter(dirPath + "\\" + DEST_FILE_NAME);
            String str = "[.ShellClassInfo]\n" + "IconResource=" + logoPath
                    + ",0\n"+"IconFile=" + logoPath
                    + "\n" + "IconIndex=0";
            fw.write(str);
        } catch (IOException e) {
            throw e;
        } finally {
            if (fw != null)
                fw.close();
        }

        Runtime.getRuntime().exec(
                "attrib +h +s " + dirPath + "\\" + DEST_FILE_NAME);
    }
}
