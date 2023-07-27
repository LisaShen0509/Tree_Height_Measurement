package com.google.ar.core.examples.java.common.fragment;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.google.ar.core.examples.java.Tree.R;
import com.google.ar.core.examples.java.common.helpers.Constants;
import com.google.ar.core.examples.java.common.helpers.DatabaseHelper;
import com.google.ar.core.examples.java.common.samplerender.MyGLRenderer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;


public class CloudFragment extends BaseFragment {

    public static final String BUNDLE_TITLE = "title";

    private static View mContentView;
    private Unbinder unbinder;
    private Long id;
    private String cloudPath;
    private Bitmap ImageBitmap=null;
    private List<Point3D> pointCloud=null;
    MyGLRenderer renderer;
    Boolean isSetRender=false;
    GLSurfaceView glView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContext = getActivity();
        mContentView = inflater.inflate(R.layout.fragment_cloud_layout, container, false);
        try {
            initData();
        } catch (IOException e) {
            e.printStackTrace();
        }
        initView();
        if(pointCloud!=null){

            renderer=new MyGLRenderer(pointCloud);
            glView.setRenderer(renderer);
            glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            glView.setOnTouchListener(new TouchListener());
            Log.d(TAG,"执行了setRenderer");
            isSetRender=true;
        }
        return mContentView;
    }

    private void initView() {
        unbinder = ButterKnife.bind(this, mContentView);
        glView = mContentView.findViewById(R.id.glview);
        glView.setEGLContextClientVersion(2);
    }
    //初始化数据
    private void initData() throws IOException {
        Bundle bundle = new Bundle();
        bundle = this.getArguments();
        id = bundle.getLong("id");
        DatabaseHelper TreeDBHelper = new DatabaseHelper(mContext, Constants.TREE_TABLE_NAME+".db",null,1);
        SQLiteDatabase TreeDB = TreeDBHelper.getReadableDatabase();
        Cursor treeCursor = TreeDB.rawQuery("select cloud from "+Constants.TREE_TABLE_NAME+" where id="+id, null);

        while (treeCursor.moveToNext()){
            cloudPath=treeCursor.getString(0);
        }
        if(!cloudPath.equals("empty")){
            File cloud = new File(cloudPath);
            if(cloud.exists()){
                pointCloud = readPlyFile(cloudPath);
            }
        }else pointCloud=null;
    }

    @OnClick({})
    public void onClick(View v) {
        switch (v.getId()) {
            default:
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onResume() {
        super.onResume();
//        try {
//            updateUI(); // 更新UI界面
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        glView.onResume();

       }

    @Override
    public void onPause() {
        super.onPause();
//        glView.onPause();

    }

    public static CloudFragment newInstance(String title) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_TITLE, title);
        CloudFragment fragment = new CloudFragment();
        fragment.setArguments(bundle);
        return fragment;
    }


    public List<Point3D> readPlyFile(String filePath) {
        List<Point3D> pointList = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            String line;
            int vertexCount = 0;
            boolean isHeaderFinished = false;

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            // 找到点云文件中所有点的最小值和最大值
            while ((line = reader.readLine()) != null) {
                if (!isHeaderFinished) {
                    if (line.startsWith("element vertex")) {
                        vertexCount = Integer.parseInt(line.split(" ")[2]);
                    }
                    if (line.startsWith("end_header")) {
                        isHeaderFinished = true;
                    }
                } else {
                    String[] split = line.split(" ");
                    float x = Float.parseFloat(split[0]);
                    float y = Float.parseFloat(split[1]);
                    float z = Float.parseFloat(split[2]);
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                    if (z < minZ) {
                        minZ = z;
                    }
                    if (z > maxZ) {
                        maxZ = z;
                    }
                }
            }
            reader.close();

            // 归一化每个点的坐标
            reader = new BufferedReader(new FileReader(filePath));
            isHeaderFinished = false;
            int cnt = 0;
            while ((line = reader.readLine()) != null && cnt < vertexCount) {
                if (!isHeaderFinished) {
//                    if (line.startsWith("element vertex")) {
//                        vertexCount = Integer.parseInt(line.split(" ")[2]);
//                    }
                    if (line.startsWith("end_header")) {
                        isHeaderFinished = true;
                    }
                } else {
                    String[] split = line.split(" ");
                    float x = Float.parseFloat(split[0]);
                    float y = Float.parseFloat(split[1]);
                    float z = Float.parseFloat(split[2]);

                    // 归一化 x,y,z 坐标
                    x = 2f * (x - minX) / (maxX - minX) - 1f;
                    y = 2f * (y - minY) / (maxY - minY) - 1f;
                    z = 2f * (z - minZ) / (maxZ - minZ) - 1f;

                    Point3D point = new Point3D(x, y, z);
                    point.blue = Integer.parseInt(split[3]);
                    point.green = Integer.parseInt(split[4]);
                    point.red = Integer.parseInt(split[5]);
                    pointList.add(point);
                    cnt++;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pointList;
    }



    public void updateUI() throws IOException {

        File cloud = new File(cloudPath);
        if(cloud.exists()){
            pointCloud = readPlyFile(cloudPath);
            glView = mContentView.findViewById(R.id.glview);
//            glView.setEGLContextClientVersion(2);
//            glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
//            glView.setOnTouchListener(new TouchListener());
            renderer=new MyGLRenderer(pointCloud);
            if(isSetRender){
                glView.requestRender();
                Log.d(TAG,"执行了requestRender");
            }
            else{
                glView.setRenderer(renderer);
                Log.d(TAG,"执行了setRenderer");
                isSetRender=true;
            }
        }
    }

    public void updateData(String newPath) {
        cloudPath=newPath;
    }

    public class Point3D {
        public float x;
        public float y;
        public float z;
        public int red;
        public int green;
        public int blue;

        public Point3D(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.red = 255; //默认颜色为白色
            this.green = 255;
            this.blue = 255;
        }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getZ() {
        return z;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public int getRed() {
        return red;
    }

    public void setRed(int red) {
        this.red = red;
    }

    public int getGreen() {
        return green;
    }

    public void setGreen(int green) {
        this.green = green;
    }

    public int getBlue() {
        return blue;
    }

    public void setBlue(int blue) {
        this.blue = blue;
    }
}

    private class TouchListener implements View.OnTouchListener {
        private float previousX, previousY;
        private boolean isRotate = true;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    previousX = event.getX();
                    previousY = event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - previousX;
                    float dy = event.getY() - previousY;
                    previousX = event.getX();
                    previousY = event.getY();

                    if (isRotate) {
                        renderer.rotate(dx, dy);
                    } else {
                        renderer.scale(dy);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    isRotate = true;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    isRotate = false;
                    break;
            }
            return true;
        }
    }

}