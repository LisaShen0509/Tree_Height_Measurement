package com.google.ar.core.examples.java.common.samplerender;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.examples.java.common.fragment.CloudFragment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLRenderer implements GLSurfaceView.Renderer {
    private List<CloudFragment.Point3D> pointList;
    private FloatBuffer vertexBuffer;// 顶点坐标缓冲区
    private FloatBuffer colorBuffer;// 颜色缓冲区

    public MyGLRenderer(List<CloudFragment.Point3D> pointList) {
        this.pointList = pointList;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
//        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);//灰色
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f); // 设置清屏颜色为白色
        prepareData();
        initShader();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(programHandle);

        int positionHandle = GLES20.glGetAttribLocation(programHandle, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, vertexBuffer);

        int colorHandle = GLES20.glGetAttribLocation(programHandle, "aColor");
        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(colorHandle, COLOR_SIZE,
                GLES20.GL_FLOAT, false,
                colorStride, colorBuffer);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pointList.size());

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);

        Matrix.setIdentityM(mvpMatrix, 0); //先将 mvpMatrix 初始化为单位矩阵

    //旋转操作
        Matrix.rotateM(mvpMatrix, 0, angleX, 0, 1, 0);
        Matrix.rotateM(mvpMatrix, 0, angleY, 1, 0, 0);

    //缩放操作
        Matrix.scaleM(mvpMatrix, 0, scale, scale, scale);

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0); //将变换矩阵传递给顶点着色器
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    String vertexShaderCode =
        "attribute vec4 vPosition;" +
                "attribute vec4 aColor;" +
                "varying vec4 vColor;" +
                "uniform mat4 uMVPMatrix;" +
                "void main() {" +
                "  gl_Position = uMVPMatrix * vPosition;" +
                "  vColor = aColor;" +
                "  gl_PointSize = 15.0;" + // 设置点的大小
                "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "varying vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private int programHandle;
    private int mvpMatrixHandle;
    private float[] mvpMatrix = new float[16];
    private int VERTEX_COUNT;//顶点数
    private final int COORDS_PER_VERTEX = 3;//每个顶点的坐标数
    private final int COLOR_SIZE = 3;//每个颜色分量数
    private final int vertexStride = COORDS_PER_VERTEX * 4;
    private final int colorStride = COLOR_SIZE * 4;

    private void initShader() {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        programHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(programHandle, vertexShader);
        GLES20.glAttachShader(programHandle, fragmentShader);
        GLES20.glLinkProgram(programHandle);

        mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    private void prepareData() {
        VERTEX_COUNT = pointList.size();

        float[] vertex = new float[VERTEX_COUNT * COORDS_PER_VERTEX];
        float[] color = new float[VERTEX_COUNT * COLOR_SIZE];

        for (int i = 0; i < VERTEX_COUNT; i++) {
            CloudFragment.Point3D point = pointList.get(i);
            vertex[i * COORDS_PER_VERTEX] = (float) point.x;
            vertex[i * COORDS_PER_VERTEX + 1] = (float) point.y;
            vertex[i * COORDS_PER_VERTEX + 2] = (float) point.z;

            color[i * COLOR_SIZE] = (float) (point.red / 255.0);
            color[i * COLOR_SIZE + 1] = (float) (point.green / 255.0);
            color[i * COLOR_SIZE + 2] = (float) (point.blue / 255.0);
        }

        ByteBuffer vbb = ByteBuffer.allocateDirect(vertex.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();
        vertexBuffer.put(vertex);
        vertexBuffer.position(0);

        ByteBuffer cbb = ByteBuffer.allocateDirect(color.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        colorBuffer = cbb.asFloatBuffer();
        colorBuffer.put(color);
        colorBuffer.position(0);
    }

    private float angleX = 0;
    private float angleY = 0;
    private final float ROTATION_FACTOR = 0.5f; //旋转因子，调整旋转速度

    public void rotate(float x, float y) {
        angleX += x * ROTATION_FACTOR;
        angleY += y * ROTATION_FACTOR;
    }

    private final float SCALE_FACTOR = 0.01f; //缩放因子，调整缩放速度
    private float scale = 1;

    public void scale(float s) {
        scale += s * SCALE_FACTOR;
    }

}

