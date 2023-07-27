package com.google.ar.core.examples.java.Tree;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.Type;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.Constants;
import com.google.ar.core.examples.java.common.helpers.DatabaseHelper;
import com.google.ar.core.examples.java.common.helpers.DepthSettings;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.InstantPlacementSettings;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.GLError;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.Texture;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.examples.java.common.samplerender.arcore.PlaneRenderer;
import com.google.ar.core.examples.java.common.samplerender.arcore.SpecularCubemapFilter;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.DeadlineExceededException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.ResourceExhaustedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static com.google.ar.core.examples.java.common.helpers.ImageHelper.RotateBitmap;
import static com.google.ar.core.examples.java.common.helpers.ImageHelper.createRootPath;
import static com.google.ar.core.examples.java.common.helpers.ImageHelper.saveBitmapFile;

public class MainActivity extends AppCompatActivity implements SampleRender.Renderer {

  private static final String TAG = MainActivity.class.getSimpleName();

  private static final String SEARCHING_PLANE_MESSAGE = "Feature point matching...";
//  private static final String WAITING_FOR_TAP_MESSAGE = "点击平面放置物体.";

  // 和渲染有关的参数
  private static final float[] sphericalHarmonicFactors = {
          0.282095f,
          -0.325735f,
          0.325735f,
          -0.325735f,
          0.273137f,
          -0.273137f,
          0.078848f,
          -0.273137f,
          0.136569f,
  };

  private static final float Z_NEAR = 0.1f; //最近平面在0.1m
  private static final float Z_FAR = 100f;//最远平面在100m

  private static final int CUBEMAP_RESOLUTION = 16;
  private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;

  // 和放置物体有关的渲染
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private TapHelper tapHelper;
  private SampleRender render;

  private PlaneRenderer planeRenderer;
  private BackgroundRenderer backgroundRenderer;
  private Framebuffer virtualSceneFramebuffer;
  private boolean hasSetTextureNames = false;
  private Image cameraImage = null;
  private Image depthImage = null;
  private Image rawdepthImage = null;
  private Image rawdepthConfidenceImage = null;//Y8
  //  private Bitmap bitmap=null;
  private Frame frame;
  //显示深度图
  private final DepthSettings depthSettings = new DepthSettings();
  private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];
  //放置实例
  private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
  private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];
  // 假设从设备相机到用户将尝试放置对象的表面的距离。
  // 这个值影响物体比例 while the tracking method of the
  // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
  // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
  // values for AR experiences where users are expected to place objects on surfaces close to the
  // camera. Use larger values for experiences where the user will likely be standing and trying to
  // place an object on the ground or floor in front of them.
  private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

  // 点云
  private VertexBuffer pointCloudVertexBuffer;
  private Mesh pointCloudMesh;
  private Shader pointCloudShader;

  //  跟踪上次渲染的点云，以避免在点云未更改时更新VBO。使用时间戳执行此操作，因为我们无法比较PointCloud对象。
  private long lastPointCloudTimestamp = 0;

  // 放置的虚拟物体
  private Mesh virtualObjectMesh;
  private Shader virtualObjectShader;
  private Texture virtualObjectAlbedoTexture;
  private Texture virtualObjectAlbedoInstantPlacementTexture;

//  private final List<WrappedAnchor> wrappedAnchors = new ArrayList<>();

  // Environmental HDR
  private Texture dfgTexture;
  private SpecularCubemapFilter cubemapFilter;

  // 此处分配的临时矩阵用于减少每个帧的分配数量。
  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16]; // view x model
  private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
  private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
  private final float[] viewInverseMatrix = new float[16];
  private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
  private final float[] viewLightDirection = new float[4]; // view x world light direction
  private Boolean checkIntri=false;
  private DatabaseHelper intriHelper;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    MaterialToolbar toolbar = findViewById(R.id.main_materialToolbar);
    back(toolbar);
    surfaceView = findViewById(R.id.surfaceview);
    //创建数据库
    DatabaseHelper helper = new DatabaseHelper(this, Constants.TREE_TABLE_NAME + ".db", null, 1);
    helper.getWritableDatabase();
    intriHelper = new DatabaseHelper(this, Constants.INTRI_TABLE_NAME + ".db", null, 1);

    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED) {
      System.out.println("已开启写入外存权限");
    } else {
      ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, 1);
    }

    // 开启点击监听
    tapHelper = new TapHelper(/*context=*/ this);
    surfaceView.setOnTouchListener(tapHelper);

    // 开启渲染器
    render = new SampleRender(surfaceView, this, getAssets());

    installRequested = false;

    //显示深度图和放置实例
    depthSettings.onCreate(this);
    instantPlacementSettings.onCreate(this);
//    ImageButton settingsButton = findViewById(R.id.settings_button);
//    settingsButton.setOnClickListener(
//        new View.OnClickListener() {
//          @Override
//          public void onClick(View v) {
//            PopupMenu popup = new PopupMenu(MainActivity.this, v);
//            popup.setOnMenuItemClickListener(MainActivity.this::settingsMenuClick);
//            popup.inflate(R.menu.settings_menu);
//            popup.show();
//          }
//        });

    //显示深度图
    Button showDepthButton = findViewById(R.id.show_depth_button);
    showDepthButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                String text = (String) showDepthButton.getText();
                if (text.equals("Show Depth")) {
                  depthSettings.setUseDepthForOcclusion(true);
                  depthSettings.setDepthColorVisualizationEnabled(true);
                  showDepthButton.setText("Hide Depth");
                } else {
                  depthSettings.setUseDepthForOcclusion(false);
                  depthSettings.setDepthColorVisualizationEnabled(false);
                  showDepthButton.setText("Show Depth");
                }
              }
            }
    );

    //保存深度图
    Button saveButton = findViewById(R.id.save_button);
    saveButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
//                saveBitmapFile(bitmap,MainActivity.this);
                if (cameraImage != null) {
                  Integer[] isSave = new Integer[4];
                  String[] objects = {
                          "RGB image",
                          "Depth Map",
                          "Raw Depth Map",
                          "Confidence Map"
                  };
                  //保存相机获取的图片
                  String ImageName = null;
                  Bitmap cameraImageBitmap = YUV_420_888_toRGB(cameraImage, cameraImage.getWidth(), cameraImage.getHeight());
                  String rootPath = createRootPath(MainActivity.this);
                  Log.d("Path", rootPath);
                  ImageName = saveBitmapFile(cameraImageBitmap, rootPath);
                  if (ImageName != null) {
                    isSave[0] = 1;
                  } else isSave[0] = 0;
                  //保存深度图
                  Image.Plane plane = depthImage.getPlanes()[0];
                  ByteBuffer buffer = plane.getBuffer().order(ByteOrder.nativeOrder());
                  byte[] data = new byte[buffer.remaining()];//此方法返回此缓冲区中剩余的元素数
                  buffer.get(data, 0, data.length);//读取缓冲区当前位置的字节，然后将位置递增。

//                  保存byte数组格式
                  isSave[1] = byteToFile(data, rootPath + "/" + ImageName + "_depthdata.txt", MainActivity.this);
                  //转成bitmap保存
//                  Bit16ToBitmap convertTool=new Bit16ToBitmap();
//                  byte[] bit8data= convertTool.bit16ToBit8(data);
//                  byte[] rgbdata=convertTool.grayToRgb(bit8data);
//                  Bitmap depthBitmap=rgb2Bitmap(rgbdata,160,120);
////                  Bitmap depthBitmap=rgb2Bitmap(data,160,120);
//                  isSave[2]=saveBitmapFileWithName(depthBitmap,rootPath,ImageName+"_depthdata",MainActivity.this);

                  //保存更高精度的深度图
                  Image.Plane rawplane = rawdepthImage.getPlanes()[0];
                  ByteBuffer rawbuffer = rawplane.getBuffer().order(ByteOrder.nativeOrder());
                  byte[] rawdata = new byte[rawbuffer.remaining()];//此方法返回此缓冲区中剩余的元素数
                  rawbuffer.get(rawdata, 0, rawdata.length);//读取缓冲区当前位置的字节，然后将位置递增。
                  //保存byte数组格式
                  isSave[2] = byteToFile(rawdata, rootPath + "/" + ImageName + "_rawdepthdata.txt", MainActivity.this);
                  //转成bitmap保存
//                  bit8data=convertTool.bit16ToBit8(rawdata);
//                  rgbdata=convertTool.grayToRgb(bit8data);
//                  Bitmap rawDepthBitmap=rgb2Bitmap(rgbdata,160,120);
////                  Bitmap rawDepthBitmap=rgb2Bitmap(rawdata,160,120);
//                  isSave[4]=saveBitmapFileWithName(rawDepthBitmap,rootPath,ImageName+"_rawdepthdata",MainActivity.this);

                  //保存置信图
                  ByteBuffer confidencebuffer = rawdepthConfidenceImage.getPlanes()[0].getBuffer().order(ByteOrder.nativeOrder());
                  byte[] cofidencedata = new byte[confidencebuffer.remaining()];//此方法返回此缓冲区中剩余的元素数
                  confidencebuffer.get(cofidencedata, 0, cofidencedata.length);//读取缓冲区当前位置的字节，然后将位置递增。
                  //保存byte数组格式
                  isSave[3] = byteToFile(cofidencedata, rootPath + "/" + ImageName + "_confidencedata.txt", MainActivity.this);

                  //转成bitmap保存
//                  bit8data=convertTool.bit16ToBit8(cofidencedata);
//                  rgbdata=convertTool.grayToRgb(bit8data);
//                  Bitmap confidenceBitmap=rgb2Bitmap(rgbdata,160,120);
////                  Bitmap confidenceBitmap=rgb2Bitmap(cofidencedata,160,120);
//                  isSave[6]=saveBitmapFileWithName(confidenceBitmap,rootPath,ImageName+"_confidencedata",MainActivity.this);


                  int allSaved = 1;
                  String message = null;
                  for (int i = 0; i < isSave.length; i++) {
                    if (isSave[i] == 0) {
                      message += objects[i] + ",";
                      allSaved = 0;
                    }
                  }
                  if (allSaved == 1) {
                    message = "Save successful";
                    messageSnackbarHelper.showMessage(MainActivity.this, message);
                    Handler handler = new Handler(); // 如果这个handler是在UI线程中创建的
                    handler.postDelayed(new Runnable() {  // 开启的runnable也会在这个handler所依附线程中运行，即主线程
                      @Override
                      public void run() {
                        // 可更新UI或做其他事情
                        // 注意这里还在当前线程，没有开启新的线程
                        messageSnackbarHelper.hide(MainActivity.this);
                      }
                    }, 3000); // 延时3s执行run内代码
                    SQLiteDatabase db = helper.getWritableDatabase();
                    //一个map集合
                    ContentValues values = new ContentValues();
                    values.put("id", Long.valueOf(ImageName));
                    values.put("image", rootPath + "/" + ImageName + ".jpg");
                    values.put("depth", rootPath + "/" + ImageName + "_depthdata.txt");
                    values.put("rawdepth", rootPath + "/" + ImageName + "_rawdepthdata.txt");
                    values.put("confidence", rootPath + "/" + ImageName + "_confidencedata.txt");
                    values.put("reldepth", "empty");
                    values.put("mask", "empty");
                    values.put("cloud", "empty");
                    try {
                      db.insert(Constants.TREE_TABLE_NAME, null, values);
                    } catch (Exception e) {
                      e.printStackTrace();
                      Log.e("Error", "保存数据到数据库失败");
                    }
                    Log.d("Message", "保存数据到数据库成功");
                    db.close();
                    // Intent 去设置要跳转的页面
                    Intent intent = new Intent(MainActivity.this, ItemDetailActivity.class);
                    intent.putExtra("id", Long.valueOf(ImageName));
                    // 进行跳转
                    startActivity(intent);
                  } else {
                    messageSnackbarHelper.showError(MainActivity.this, message.substring(0, message.length() - 1) + "保存失败");
                    Handler handler = new Handler(); // 如果这个handler是在UI线程中创建的
                    handler.postDelayed(new Runnable() {  // 开启的runnable也会在这个handler所依附线程中运行，即主线程
                      @Override
                      public void run() {
                        // 可更新UI或做其他事情
                        // 注意这里还在当前线程，没有开启新的线程
                        messageSnackbarHelper.hide(MainActivity.this);
                      }
                    }, 3000); // 延时3s执行run内代码
                  }

                } else {
                  Toast.makeText(MainActivity.this, "The depth map has not been generated", Toast.LENGTH_SHORT).show();
                }
              }
            }
    );
  }

  private Bitmap YUV_420_888_toRGB(Image image, int width, int height) {
    // 获取3个图像平面
    Image.Plane[] planes = image.getPlanes();
    ByteBuffer buffer = planes[0].getBuffer();
    byte[] y = new byte[buffer.remaining()];
    buffer.get(y);

    buffer = planes[1].getBuffer();
    byte[] u = new byte[buffer.remaining()];
    buffer.get(u);

    buffer = planes[2].getBuffer();
    byte[] v = new byte[buffer.remaining()];
    buffer.get(v);

    // 获取相关的行步长和像素步长
    // y的像素步长为1
    int yRowStride = planes[0].getRowStride();
    int uvRowStride = planes[1].getRowStride();  // u和v的行步长相同
    int uvPixelStride = planes[1].getPixelStride();  //u和v的像素步长相同


    // rs只创建一次
    RenderScript rs = RenderScript.create(this);
    //RenderScript rs = MainActivity.rs;
    ScriptC_yuv420888 mYuv420 = new ScriptC_yuv420888(rs);

    // Y,U,V are defined as global allocations, the out-Allocation is the Bitmap.
    // Note also that uAlloc and vAlloc are 1-dimensional while yAlloc is 2-dimensional.
    Type.Builder typeUcharY = new Type.Builder(rs, Element.U8(rs));

    //using safe height
    typeUcharY.setX(yRowStride).setY(y.length / yRowStride);

    Allocation yAlloc = Allocation.createTyped(rs, typeUcharY.create());
    yAlloc.copyFrom(y);
    mYuv420.set_ypsIn(yAlloc);

    Type.Builder typeUcharUV = new Type.Builder(rs, Element.U8(rs));
    // note that the size of the u's and v's are as follows:
    //      (  (width/2)*PixelStride + padding  ) * (height/2)
    // =    (RowStride                          ) * (height/2)
    // but I noted that on the S7 it is 1 less...
    typeUcharUV.setX(u.length);
    Allocation uAlloc = Allocation.createTyped(rs, typeUcharUV.create());
    uAlloc.copyFrom(u);
    mYuv420.set_uIn(uAlloc);

    Allocation vAlloc = Allocation.createTyped(rs, typeUcharUV.create());
    vAlloc.copyFrom(v);
    mYuv420.set_vIn(vAlloc);

    // handover parameters
    mYuv420.set_picWidth(width);
    mYuv420.set_uvRowStride(uvRowStride);
    mYuv420.set_uvPixelStride(uvPixelStride);

    Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Allocation outAlloc = Allocation.createFromBitmap(rs, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);

    Script.LaunchOptions lo = new Script.LaunchOptions();
    lo.setX(0, width);  // by this we ignore the y’s padding zone, i.e. the right side of x between width and yRowStride
    //using safe height
    lo.setY(0, y.length / yRowStride);

    mYuv420.forEach_doConvert(outAlloc, lo);
    outAlloc.copyTo(outBitmap);
    return RotateBitmap(outBitmap, 90);
  }

  /** Menu button to launch feature specific settings. */
//  protected boolean settingsMenuClick(MenuItem item) {
//    if (item.getItemId() == R.id.depth_settings) {
//      launchDepthSettingsMenuDialog();
//      return true;
//    } else if (item.getItemId() == R.id.instant_placement_settings) {
//      launchInstantPlacementSettingsMenuDialog();
//      return true;
//    }
//    return false;
//  }
  @Override
  protected void onDestroy() {
    if (session != null) {
      session.close();
      session = null;
    }
    super.onDestroy();
  }

  //恢复会话
  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

//        获取相机权限
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // 创建会话
        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
              | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please Update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please reinstall the app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "The device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create an AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // 注意，顺序很重要-参阅onPause（）中的注释，这里的情况正好相反。
    try {
      configureSession();
      session.resume();
    } catch (CameraNotAvailableException e) {
      messageSnackbarHelper.showError(this, "Unable to call camera, please restart app");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
//      请注意，顺序很重要-GLSurfaceView首先暂停，这样它就不会尝试查询会话。
//      如果会话在GLSurfaceView之前暂停，GLSurfaceView仍可能调用Session.update（）并产生SessionPausedException。
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  //申请权限，自动调用
  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permissions are required", Toast.LENGTH_LONG)
              .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // 权限被拒绝，勾选“不再询问”。
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  //首次进入一个Activity后会在onResume()方法后面调用
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(SampleRender render) {
    // 准备渲染对象。这涉及读取着色器和3D模型文件，因此可能引发IOException。
    try {
//      planeRenderer = new PlaneRenderer(render);
      backgroundRenderer = new BackgroundRenderer(render);
      virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);

//      cubemapFilter =
//          new SpecularCubemapFilter(
//              render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);
      // 环境照明的负载DFG查找表
      dfgTexture =
              new Texture(
                      render,
                      Texture.Target.TEXTURE_2D,
                      Texture.WrapMode.CLAMP_TO_EDGE,
                      /*useMipmaps=*/ false);
      // The dfg.raw file is a raw half-float texture with two channels.
      final int dfgResolution = 64;
      final int dfgChannels = 2;
      final int halfFloatSize = 2;

      ByteBuffer buffer =
              ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
      try (InputStream is = getAssets().open("models/dfg.raw")) {
        is.read(buffer.array());
      }
      // SampleRender abstraction leaks here.
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
      GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
      GLES30.glTexImage2D(
              GLES30.GL_TEXTURE_2D,
              /*level=*/ 0,
              GLES30.GL_RG16F,
              /*width=*/ dfgResolution,
              /*height=*/ dfgResolution,
              /*border=*/ 0,
              GLES30.GL_RG,
              GLES30.GL_HALF_FLOAT,
              buffer);
      GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");

      // 点云
      pointCloudShader =
              Shader.createFromAssets(
                              render, "shaders/point_cloud.vert", "shaders/point_cloud.frag", /*defines=*/ null)
                      .setVec4(
                              "u_Color", new float[]{31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                      .setFloat("u_PointSize", 5.0f);
      // four entries per vertex: X, Y, Z, confidence
      pointCloudVertexBuffer =
              new VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null);
      final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
      pointCloudMesh =
              new Mesh(
                      render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers);

      // 放置的虚拟坐标物
      virtualObjectAlbedoTexture =
              Texture.createFromAsset(
                      render,
                      "models/pawn_albedo.png",
                      Texture.WrapMode.CLAMP_TO_EDGE,
                      Texture.ColorFormat.SRGB);
      virtualObjectAlbedoInstantPlacementTexture =
              Texture.createFromAsset(
                      render,
                      "models/pawn_albedo_instant_placement.png",
                      Texture.WrapMode.CLAMP_TO_EDGE,
                      Texture.ColorFormat.SRGB);
      Texture virtualObjectPbrTexture =
              Texture.createFromAsset(
                      render,
                      "models/pawn_roughness_metallic_ao.png",
                      Texture.WrapMode.CLAMP_TO_EDGE,
                      Texture.ColorFormat.LINEAR);

      virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
//      virtualObjectShader =
//          Shader.createFromAssets(
//                  render,
//                  "shaders/environmental_hdr.vert",
//                  "shaders/environmental_hdr.frag",
//                  /*defines=*/ new HashMap<String, String>() {
//                    {
//                      put(
//                          "NUMBER_OF_MIPMAP_LEVELS",
//                          Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
//                    }
//                  }
//                  )
//              .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
//              .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
//              .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
//              .setTexture("u_DfgTexture", dfgTexture);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
    }
  }

  @Override
  public void onSurfaceChanged(SampleRender render, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    virtualSceneFramebuffer.resize(width, height);
  }

  @Override
  public void onDrawFrame(SampleRender render) {
    if (session == null) {
      return;
    }

//    纹理名称应该只在GL线程上设置一次，除非它们发生了变化。
//    这是在onDrawFrame而不是onSurfaceCreated期间完成的，因为会话在onSurfaceCreated执行期间不保证已经初始化。
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(
              new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
      hasSetTextureNames = true;
    }

    // -- 更新每帧状态

//    通知ARCore会话视图大小改变，以便透视矩阵和视频背景可以适当调整。
    displayRotationHelper.updateSessionIfNeeded(session);

//    从ARSession中获取当前帧。当配置设置为UpdateMode时。BLOCKING(默认值)，这将限制渲染到相机帧率。
    try {
      frame = session.update();
    } catch (CameraNotAvailableException e) {
      Log.e(TAG, "The camera is not available when onDrawFrame", e);
      messageSnackbarHelper.showError(this, "Camera is not available, please restart");
      return;
    }
    Camera camera = frame.getCamera();
    if(checkIntri==false){
      SQLiteDatabase IntriDB = intriHelper.getReadableDatabase();
      Cursor intriCursor = IntriDB.rawQuery("select * from "+Constants.INTRI_TABLE_NAME, null);
      Float fx=null;
      while (intriCursor.moveToNext()) {
        fx=intriCursor.getFloat(0);
      }
      if(fx!=null){
        checkIntri=true;
      }
      else{
        CameraIntrinsics intrinsics = camera.getImageIntrinsics();
        float focalLengthX = intrinsics.getFocalLength()[0]; // 焦距 x 分量
        float focalLengthY = intrinsics.getFocalLength()[1]; // 焦距 y 分量
        float principalPointX = intrinsics.getPrincipalPoint()[0]; // 光心 x 分量
        float principalPointY = intrinsics.getPrincipalPoint()[1]; // 光心 y 分量
//      int imageWidth = intrinsics.getImageDimensions()[0]; // 图像宽度
//      int imageHeight = intrinsics.getImageDimensions()[1]; // 图像高度
        Log.d("focalLengthX",focalLengthX+"");
        Log.d("focalLengthY",focalLengthY+"");
        Log.d("principalPointX",principalPointX+"");
        Log.d("principalPointY",principalPointY+"");
        IntriDB = intriHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("fx",focalLengthY);
        values.put("fy",focalLengthX);
        values.put("cx",principalPointY);
        values.put("cy",principalPointX);
        IntriDB.insert(Constants.INTRI_TABLE_NAME, null,values);
        checkIntri=true;
      }
    }

    // 更新背景渲染器状态以匹配深度设置。
    try {
      backgroundRenderer.setUseDepthVisualization(
              render, depthSettings.depthColorVisualizationEnabled());
      backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
      return;
    }
//    必须每帧调用 BackgroundRenderer.updateDisplayGeometry 以更新用于绘制背景相机图像的坐标。
    backgroundRenderer.updateDisplayGeometry(frame);

    if (camera.getTrackingState() == TrackingState.TRACKING
            && (depthSettings.useDepthForOcclusion()
            || depthSettings.depthColorVisualizationEnabled())) {
      try {
        if (cameraImage != null)
          cameraImage.close();
        cameraImage = frame.acquireCameraImage();//AIMAGE_FORMAT_YUV_420_888.

        if (depthImage != null)
          depthImage.close();
        depthImage = frame.acquireDepthImage16Bits();
        backgroundRenderer.updateCameraDepthTexture(depthImage);

        if (rawdepthImage != null)
          rawdepthImage.close();
        rawdepthImage = frame.acquireRawDepthImage16Bits();

        if (rawdepthConfidenceImage != null)
          rawdepthConfidenceImage.close();
        rawdepthConfidenceImage = frame.acquireRawDepthConfidenceImage();
      } catch (NotYetAvailableException e) {
        // This normally means that depth data is not available yet. This is normal so we will not
        // spam the logcat with this.
//        e.printStackTrace();
      } catch (NullPointerException e) {
        e.printStackTrace();
      } catch (DeadlineExceededException e) {//if the input frame is not the current frame.
        e.printStackTrace();
      } catch (ResourceExhaustedException e) {
        //如果调用方应用程序已经超过了它可以保存而不释放的最大图像数量。
//        e.printStackTrace();
      }
    }

    // Handle one tap per frame.
//    handleTap(frame, camera);

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

    String message = null;
    if (camera.getTrackingState() == TrackingState.PAUSED) {  //已暂停特征点匹配
      if (camera.getTrackingFailureReason() == TrackingFailureReason.NONE) {  //还在生成深度图
        message = SEARCHING_PLANE_MESSAGE;
      } else {  //无法生成深度图
        message = TrackingStateHelper.getTrackingFailureReasonString(camera);
      }
    } else if (hasTrackingPlane()) {  //已生成深度图
//      if (wrappedAnchors.isEmpty()) {
//        message = WAITING_FOR_TAP_MESSAGE;
//      }
    } else {
      message = SEARCHING_PLANE_MESSAGE;
    }
    if (message == null) {
//      messageSnackbarHelper.hide(this);
    } else {
      messageSnackbarHelper.showMessage(this, message);
      Handler handler = new Handler(); // 如果这个handler是在UI线程中创建的
      handler.postDelayed(new Runnable() {  // 开启的runnable也会在这个handler所依附线程中运行，即主线程
        @Override
        public void run() {
          // 可更新UI或做其他事情
          // 注意这里还在当前线程，没有开启新的线程
          messageSnackbarHelper.hide(MainActivity.this);
        }
      }, 3000); // 延时3s执行run内代码

    }

    // -- Draw background

    if (frame.getTimestamp() != 0) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render);
    }

    // If not tracking, don't draw 3D objects.
    if (camera.getTrackingState() == TrackingState.PAUSED) {
      return;
    }

    // -- Draw non-occluded virtual objects (planes, point cloud)

    // 得到用于在相机图像顶部渲染虚拟内容的投影矩阵。
    //注意，投影矩阵反映了当前显示几何图形和显示旋转。
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

    // 返回此帧相机的视图矩阵。请注意，视图矩阵包含显示方向。
    camera.getViewMatrix(viewMatrix, 0);

    // 显示出匹配的特征点
    // 使用try-with-resources自动释放点云。
    try (PointCloud pointCloud = frame.acquirePointCloud()) {
      if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
        pointCloudVertexBuffer.set(pointCloud.getPoints());
        lastPointCloudTimestamp = pointCloud.getTimestamp();
      }
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
      pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
      render.draw(pointCloudMesh, pointCloudShader);
    }

    // Visualize planes.
//    planeRenderer.drawPlanes(
//        render,
//        session.getAllTrackables(Plane.class),
//        camera.getDisplayOrientedPose(),
//        projectionMatrix);

    // -- Draw occluded virtual objects

    // Update lighting parameters in the shader
    updateLightEstimation(frame.getLightEstimate(), viewMatrix);

    // Visualize anchors created by touch.
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
//    for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
//      Anchor anchor = wrappedAnchor.getAnchor();
//      Trackable trackable = wrappedAnchor.getTrackable();
//      if (anchor.getTrackingState() != TrackingState.TRACKING) {
//        continue;
//      }
//
//      // Get the current pose of an Anchor in world space. The Anchor pose is updated
//      // during calls to session.update() as ARCore refines its estimate of the world.
//      anchor.getPose().toMatrix(modelMatrix, 0);
//
//      // Calculate model/view/projection matrices
//      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
//      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
//
//      // Update shader properties and draw
//      virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
//      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
//
//      if (trackable instanceof InstantPlacementPoint
//          && ((InstantPlacementPoint) trackable).getTrackingMethod()
//              == InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE) {
//        virtualObjectShader.setTexture(
//            "u_AlbedoTexture", virtualObjectAlbedoInstantPlacementTexture);
//      } else {
//        virtualObjectShader.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture);
//      }
//
//      render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
//    }

    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
  }

  /**
   * @param contents byte文件数组
   * @param filePath 文件存放目录及文件名，包括文件名及其后缀
   * @Title: byteToFile
   */
  private static int byteToFile(byte[] contents, String filePath, Activity activity) {
    BufferedInputStream bis = null;
    FileOutputStream fos = null;
    BufferedOutputStream output = null;
    try {
      ByteArrayInputStream byteInputStream = new ByteArrayInputStream(contents);
      bis = new BufferedInputStream(byteInputStream);
      File file = new File(filePath);
      // 获取文件的父路径字符串
      File path = file.getParentFile();
      if (!path.exists()) {
        boolean isCreated = path.mkdirs();
        if (!isCreated) {
          System.out.println("==========> 文件夹创建失败");
          return 0;
        }
      }
      fos = new FileOutputStream(file);
      // 实例化OutputString 对象
      output = new BufferedOutputStream(fos);
      byte[] buffer = new byte[1024];
      int length = bis.read(buffer);
      while (length != -1) {
        output.write(buffer, 0, length);
        length = bis.read(buffer);
      }
      output.flush();
      Log.d(TAG, "save data success");
    } catch (Exception e) {
      System.out.println("==========> 文件保存失败");
      return 0;
    } finally {
      try {
        bis.close();
        fos.close();
        output.close();
      } catch (IOException e) {
        System.out.println("==========> 文件流关闭失败");
      }
    }
    return 1;
  }

  protected void back(MaterialToolbar toolbar) {
    toolbar.setNavigationOnClickListener(v -> onBackPressed());
  }
  // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
//  private void handleTap(Frame frame, Camera camera) {
//    MotionEvent tap = tapHelper.poll();
//    if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
//      List<HitResult> hitResultList;
//      if (instantPlacementSettings.isInstantPlacementEnabled()) {
//        int Height=depthImage.getHeight();
//        int Width=depthImage.getWidth();
//        messageSnackbarHelper.showMessageWithDismiss(this,Height+","+Width);//120,160
//        DisplayMetrics dm = new DisplayMetrics();
//        getWindowManager().getDefaultDisplay().getMetrics(dm);
//        int x=(int)(tap.getX()/dm.widthPixels*Width);
//        int y=(int)(tap.getY()/dm.heightPixels*Height);
//        int depth=getMillimetersDepth(depthImage,x,y);
//        messageSnackbarHelper.showMessageWithDismiss(this,"当前点距离："+depth+"mm");
////        messageSnackbarHelper.showMessageWithDismiss(this, (int)tap.getX()+","+(int)tap.getY());
//        Log.d(TAG,"当前点距离："+depth+"mm");
//        hitResultList =
//            frame.hitTestInstantPlacement(tap.getX(), tap.getY(), APPROXIMATE_DISTANCE_METERS);
//
//      } else {
//        hitResultList = frame.hitTest(tap);
//      }
//
//      for (HitResult hit : hitResultList) {
//        // If any plane, Oriented Point, or Instant Placement Point was hit, create an anchor.
//        Trackable trackable = hit.getTrackable();
//        // If a plane was hit, check that it was hit inside the plane polygon.
//        // DepthPoints are only returned if Config.DepthMode is set to AUTOMATIC.
//        if ((trackable instanceof Plane
//                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
//                && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
//            || (trackable instanceof Point
//                && ((Point) trackable).getOrientationMode()
//                    == OrientationMode.ESTIMATED_SURFACE_NORMAL)
//            || (trackable instanceof InstantPlacementPoint)
//            || (trackable instanceof DepthPoint)) {
//          // Cap the number of objects created. This avoids overloading both the
//          // rendering system and ARCore.
//          if (wrappedAnchors.size() >= 20) {
//            wrappedAnchors.get(0).getAnchor().detach();
//            wrappedAnchors.remove(0);
//          }
//
//          // Adding an Anchor tells ARCore that it should track this position in
//          // space. This anchor is created on the Plane to place the 3D model
//          // in the correct position relative both to the world and to the plane.
//          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));
//          // For devices that support the Depth API, shows a dialog to suggest enabling
//          // depth-based occlusion. This dialog needs to be spawned on the UI thread.
//          this.runOnUiThread(this::showOcclusionDialogIfNeeded);
//
//          // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
//          // Instant Placement Point.
//          break;
//        }
//      }
//    }
//  }

  /**
   * Shows a pop-up dialog on the first call, determining whether the user wants to enable
   * depth-based occlusion. The result of this dialog can be retrieved with useDepthForOcclusion().
   */
  private void showOcclusionDialogIfNeeded() {
    boolean isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
    if (!depthSettings.shouldShowDepthEnableDialog() || !isDepthSupported) {
      return; // Don't need to show dialog.
    }

    // Asks the user whether they want to use depth-based occlusion.
    new AlertDialog.Builder(this)
            .setTitle(R.string.options_title_with_depth)
            .setMessage(R.string.depth_use_explanation)
            .setPositiveButton(
                    R.string.button_text_enable_depth,
                    (DialogInterface dialog, int which) -> {
                      depthSettings.setUseDepthForOcclusion(true);
                    })
            .setNegativeButton(
                    R.string.button_text_disable_depth,
                    (DialogInterface dialog, int which) -> {
                      depthSettings.setUseDepthForOcclusion(false);
                    })
            .show();
  }

//  private void launchInstantPlacementSettingsMenuDialog() {
//    resetSettingsMenuDialogCheckboxes();
//    Resources resources = getResources();
//    new AlertDialog.Builder(this)
//        .setTitle(R.string.options_title_instant_placement)
//        .setMultiChoiceItems(
//            resources.getStringArray(R.array.instant_placement_options_array),
//            instantPlacementSettingsMenuDialogCheckboxes,
//            (DialogInterface dialog, int which, boolean isChecked) ->
//                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
//        .setPositiveButton(
//            R.string.done,
//            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
//        .setNegativeButton(
//            android.R.string.cancel,
//            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
//        .show();
//  }

  /** Shows checkboxes to the user to facilitate toggling of depth-based effects. */
//  private void launchDepthSettingsMenuDialog() {
//    // Retrieves the current settings to show in the checkboxes.
//    resetSettingsMenuDialogCheckboxes();
//
//    // Shows the dialog to the user.
//    Resources resources = getResources();
//    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
//      // With depth support, the user can select visualization options.
//      new AlertDialog.Builder(this)
//          .setTitle(R.string.options_title_with_depth)
//          .setMultiChoiceItems(
//              resources.getStringArray(R.array.depth_options_array),
//              depthSettingsMenuDialogCheckboxes,
//              (DialogInterface dialog, int which, boolean isChecked) ->
//                  depthSettingsMenuDialogCheckboxes[which] = isChecked)
//          .setPositiveButton(
//              R.string.done,
//              (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
//          .setNegativeButton(
//              android.R.string.cancel,
//              (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
//          .show();
//    } else {
//      // Without depth support, no settings are available.
//      new AlertDialog.Builder(this)
//          .setTitle(R.string.options_title_without_depth)
//          .setPositiveButton(
//              R.string.done,
//              (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
//          .show();
//    }
//  }

//  private void applySettingsMenuDialogCheckboxes() {
//    depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
//    depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
//    instantPlacementSettings.setInstantPlacementEnabled(
//        instantPlacementSettingsMenuDialogCheckboxes[0]);
//    configureSession();
//  }
//
//  private void resetSettingsMenuDialogCheckboxes() {
//    depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
//    depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
//    instantPlacementSettingsMenuDialogCheckboxes[0] =
//        instantPlacementSettings.isInstantPlacementEnabled();
//  }

  /** Checks if we detected at least one plane. */
  private boolean hasTrackingPlane() {
    for (Plane plane : session.getAllTrackables(Plane.class)) {
      if (plane.getTrackingState() == TrackingState.TRACKING) {
        return true;
      }
    }
    return false;
  }

  /** Update state based on the current frame's light estimation. */
  private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
    if (lightEstimate.getState() != LightEstimate.State.VALID) {
//      virtualObjectShader.setBool("u_LightEstimateIsValid", false);
      return;
    }
//    virtualObjectShader.setBool("u_LightEstimateIsValid", true);

    Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
//    virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);

    updateMainLight(
            lightEstimate.getEnvironmentalHdrMainLightDirection(),
            lightEstimate.getEnvironmentalHdrMainLightIntensity(),
            viewMatrix);
//    updateSphericalHarmonicsCoefficients(
//        lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
//    cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
  }

  private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
    // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
    worldLightDirection[0] = direction[0];
    worldLightDirection[1] = direction[1];
    worldLightDirection[2] = direction[2];
    Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
//    virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
//    virtualObjectShader.setVec3("u_LightIntensity", intensity);
  }

  //与物体渲染有关
  private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
//     在将球面谐波系数传递到着色器之前，对其进行预乘
//     sphericalHarmonicFactors中的常数由三个项导出:
//
//    1.归一化球面谐波基函数（y_lm）
//
//    2.朗伯扩散BRDF因子（1/pi）
//
//    3.一个＜cos＞卷积。这样做是为了使生成的函数输出给定曲面法线的半球上所有入射光的辐照度，
//    这是着色器（environment_hdr.frag）所期望的。
//
//     You can read more details about the math here:
//     https://google.github.io/filament/Filament.html#annex/sphericalharmonics

    if (coefficients.length != 9 * 3) {
      throw new IllegalArgumentException(
              "The given coefficients array must be of length 27 (3 components per 9 coefficients");
    }

    // Apply each factor to every component of each coefficient
    for (int i = 0; i < 9 * 3; ++i) {
      sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
    }
//    virtualObjectShader.setVec3Array(
//        "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
  }

  /** 使用功能设置配置会话。 */
  private void configureSession() throws CameraNotAvailableException {
    // 为会话创建相机配置筛选器。
    CameraConfigFilter filter = new CameraConfigFilter(session);
    List<CameraConfig> cameraConfigList = session.getSupportedCameraConfigs(filter);
    int len = cameraConfigList.size();
    int MaxH = -1;
    int MaxIndx = 0;
    for (int i = 0; i < len; i++) {
//      Log.d("i",String.valueOf(i));
//      Log.d("CameraId",cameraConfigList.get(i).getCameraId());
//      Log.d("ImageSize",cameraConfigList.get(i).getImageSize().toString());
      if (cameraConfigList.get(i).getImageSize().getHeight() > MaxH) {
        MaxIndx = i;
        MaxH = cameraConfigList.get(i).getImageSize().getHeight();
      }
    }
//    Log.d("MaxIndx", String.valueOf(MaxIndx));
//    Log.d("MaxImageSize",cameraConfigList.get(MaxIndx).getImageSize().toString());
    session.setCameraConfig(cameraConfigList.get(MaxIndx));
    CameraConfig cameraConfig = session.getCameraConfig();
    Log.d("CurrentImageSize", cameraConfig.getImageSize().toString());

    Config config = session.getConfig();
    //启用照明估计，在线性颜色空间中生成推断的环境HDR（高动态光照渲染）照明估计。
    config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
    //若设备支持深度，启用深度
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
      config.setDepthMode(Config.DepthMode.AUTOMATIC);
    } else {
      config.setDepthMode(Config.DepthMode.DISABLED);
    }
    config.setFocusMode(Config.FocusMode.AUTO);
    //判断是否启动实例放置
//    if (instantPlacementSettings.isInstantPlacementEnabled()) {
//      config.setInstantPlacementMode(InstantPlacementMode.LOCAL_Y_UP);
//    } else {
//      config.setInstantPlacementMode(InstantPlacementMode.DISABLED);
//    }
    // 配置会话并验证当前设置的相机配置是否支持指定会话配置中已启用的功能。
    session.configure(config);

  }

  /** Obtain the depth in millimeters for depthImage at coordinates (x, y). */
  private int getMillimetersDepth(Image depthImage, int x, int y) {
    // The depth image has a single plane, which stores depth for each
    // pixel as 16-bit unsigned integers.
    Image.Plane plane = depthImage.getPlanes()[0];
    int byteIndex = x * plane.getPixelStride() + y * plane.getRowStride();
    //plane.getPixelStride():The distance between adjacent pixel samples, in bytes.
    //plane.getRowStride():The row stride for this color plane, in bytes.
    ByteBuffer buffer = plane.getBuffer().order(ByteOrder.nativeOrder());
    //plane.getBuffer():Get a direct ByteBuffer containing the frame data.
    //ByteOrder.nativeOrder():Retrieves the native byte order of the underlying platform.检索底层平台的本机字节顺序。
//    return buffer.getShort(byteIndex);
    //getShort用于读取此缓冲区当前位置的下两个字节，根据当前字节顺序将它们组合成一个短值，然后将位置递增 2。
    short depthSample = buffer.getShort(byteIndex);
    // Only the lowest 13 bits are used to represent depth in millimeters.
    return (depthSample & 0x1FFF);
  }

}

/**
 * Associates an Anchor with the trackable it was attached to. This is used to be able to check
 * whether or not an Anchor originally was attached to an {@link InstantPlacementPoint}.
 */
//class WrappedAnchor {
//  private Anchor anchor;
//  private Trackable trackable;
//
//  public WrappedAnchor(Anchor anchor, Trackable trackable) {
//    this.anchor = anchor;
//    this.trackable = trackable;
//  }
//
//  public Anchor getAnchor() {
//    return anchor;
//  }
//
//  public Trackable getTrackable() {
//    return trackable;
//  }
//}
