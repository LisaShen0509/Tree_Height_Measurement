package com.google.ar.core.examples.java.common.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Environment;
import android.util.Log;

import com.google.ar.core.examples.java.Tree.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.graphics.ImageDecoder.decodeBitmap;

public class ImageHelper {
    /**
     * Compresses the file to make a bitmap of size, passed in arguments
     * @param width width you want your bitmap to have
     * @param height hight you want your bitmap to have.
     * @param f file with image
     * @return bitmap object of sizes, passed in arguments
     */
    public static Bitmap getCompressedBitmap(int width, int height, File f) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), options);

        options.inSampleSize = calculateInSampleSize(options, width, height);
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(f.getAbsolutePath(), options);
    }

    public static byte[] getContent(String filePath) throws IOException {
        File file = new File(filePath);
        long fileSize = file.length();
        if (fileSize > Integer.MAX_VALUE) {
            System.out.println("file too big...");
            return null;
        }
        FileInputStream fi = new FileInputStream(file);
        byte[] buffer = new byte[(int) fileSize];
        int offset = 0;
        int numRead = 0;
        while (offset < buffer.length
                && (numRead = fi.read(buffer, offset, buffer.length - offset)) >= 0) {
            offset += numRead;
        }
        // 确保所有数据均被读取
        if (offset != buffer.length) {
            throw new IOException("Could not completely read file "
                    + file.getName());
        }
        fi.close();
        return buffer;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 图片原始尺寸
        Integer height = options.outHeight;
        Integer width = options.outWidth;
//        System.out.println("图片原始尺寸:"+height.toString()+","+width.toString());
        Integer inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            Integer halfHeight = height / 2;
            Integer halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
    public static Bitmap RotateBitmap(Bitmap original,Integer rotateDegree){
        //顺时针旋转90度
        Matrix matrix = new Matrix(); // 创建操作图片用的矩阵对象
        matrix.postRotate(rotateDegree); // 执行图片的旋转动作
        // 创建并返回旋转后的位图对象
        return Bitmap.createBitmap(original, 0, 0, original.getWidth(),
                original.getHeight(), matrix, false);
    }

    public static int convertByteToInt(byte data) {
        int heightBit = (int) ((data >> 4) & 0x0F);
        int lowBit = (int) (0x0F & data);
        return heightBit * 16 + lowBit;
    }

    public static int[] convertByteToColor(byte[] data, int width, int height) {
        int size = data.length;
        if (size == 0) {
            return null;
        }
        // 一般RGB字节数组的长度应该是3的倍数，
        // 不排除有特殊情况，多余的RGB数据用黑色0XFF000000填充
        int[] color = new int[width * height];
        int red, green, blue;
        for (int i = 0; i < width * height; ++i) {
            red = convertByteToInt(data[i]);
            green = convertByteToInt(data[i]);
            blue = convertByteToInt(data[i]);

            // 获取RGB分量值通过按位或生成int的像素值
            color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
        }
        return color;
    }

    static public Bitmap rgb2Bitmap(byte[] data, int width, int height) {
        int[] colors;
        colors = convertByteToColor(data, width, height);
        if (colors == null) {
            return null;
        }

        Bitmap bmp = Bitmap.createBitmap(colors, 0, width, width, height,
                Bitmap.Config.ARGB_8888);
        return bmp;
    }

    static public Bitmap getBitmapByPath(String path) throws IOException {

        // Read depth data from file
        byte[] bytes = new byte[(int) new File(path).length()];
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path));
        bis.read(bytes, 0, bytes.length);
        bis.close();
        int[] depthData = new int[bytes.length / 2];
        for (int i = 0; i < bytes.length; i += 2) {
            depthData[i / 2] = ((bytes[i + 1] & 0xff) << 8) | (bytes[i] & 0xff);
        }

        // Map depth values to color
        int width = 160;
        int height = 120;
        int[] colors = new int[width * height];
        for (int i = 0; i < depthData.length; i++) {
            int depth = depthData[i];
            if (depth == 0) {
                colors[i] = Color.BLACK;
            } else {
                int gray = (int) (255 - (depth * (255.0f / 65535.0f)));
                colors[i] = Color.rgb(gray, gray, gray);
            }
        }
        // Create bitmap
        Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);

        Bitmap outmap=RotateBitmap(bitmap,90);
        return outmap;

    }

    /**
     * 创建根缓存目录
     *
     * @return
     */
    public static String createRootPath(Context context) {
        String cacheRootPath = "";
        if (isSdCardAvailable()) {
            // /storage/emulated/0/
//      cacheRootPath = context.getExternalCacheDir().getPath();//相机访问不了
            cacheRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            Log.d("TAG","--------------------可访问sd卡---------------");
        } else {
            // /data/data/<application package>/cache
            Log.d("TAG","--------------------不可访问sd卡---------------");
            cacheRootPath = context.getCacheDir().getPath();
        }
        return cacheRootPath;
    }

    public static boolean isSdCardAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 保存彩色图，生成文件名
     * @param bitmap
     * @param rootPath
     * @return
     */
    public static String saveBitmapFile(Bitmap bitmap,String rootPath){
        String ImageName=String.valueOf(System.currentTimeMillis());
        File file = new File(rootPath+"/" + ImageName + ".jpg");
//    File file = new File("/data/data/com.google.ar.core.examples.java.Depth/cache/" + ImageName + ".jpg");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //通知系统相册刷新，file是要保存图片
//    activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
//            Uri.fromFile(new File(file.getPath()))));

        Log.d("xxx", "saveBitmap: " + file.getAbsolutePath());
        return ImageName;
    }


    public static int saveBitmapFileWithName(Bitmap bitmap, String rootPath,String ImageName){
        File file = new File(rootPath+"/" + ImageName + ".jpg");
    //    File file = new File("/data/data/com.google.ar.core.examples.java.Depth/cache/" + ImageName + ".jpg");
        if (!file.getParentFile().exists()) {
          file.getParentFile().mkdirs();
        }
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
            bos.close();
        } catch (IOException e) {
          e.printStackTrace();
          return 0;
        }
        //通知系统相册刷新，file是要保存图片
    //    activity.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
    //            Uri.fromFile(new File(file.getPath()))));

        Log.d("xxx", "saveBitmap: " + file.getAbsolutePath());
        return 1;
  }

}
