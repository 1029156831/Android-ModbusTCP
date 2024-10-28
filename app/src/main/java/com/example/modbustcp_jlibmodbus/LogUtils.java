package com.example.modbustcp_jlibmodbus;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtils {

    private static final String LOG_FILE_NAME = "gzzd.txt"; // 日志文件名

    // 记录日志信息
    public static void logError(Context context, String message) {
        FileOutputStream fos = null;
        try {
            // 获取应用的文件目录
            File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
            fos = new FileOutputStream(logFile, true); // true表示追加写入
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String logMessage = timestamp + " : " + message + "\n";
            fos.write(logMessage.getBytes()); // 写入日志信息
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常堆栈
        } finally {
            if (fos != null) {
                try {
                    fos.close(); // 关闭文件流
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
