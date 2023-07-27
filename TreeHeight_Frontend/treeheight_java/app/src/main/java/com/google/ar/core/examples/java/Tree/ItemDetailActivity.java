package com.google.ar.core.examples.java.Tree;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.ar.core.examples.java.common.MyAdapter;
import com.google.ar.core.examples.java.common.ResponceData;
import com.google.ar.core.examples.java.common.fragment.AbsDepthFragment;
import com.google.ar.core.examples.java.common.fragment.AlignedDepthFragment;
import com.google.ar.core.examples.java.common.fragment.CloudFragment;
import com.google.ar.core.examples.java.common.fragment.MaskFragment;
import com.google.ar.core.examples.java.common.fragment.RGBFragment;
import com.google.ar.core.examples.java.common.helpers.Constants;
import com.google.ar.core.examples.java.common.helpers.DatabaseHelper;
import com.google.ar.core.examples.java.common.helpers.ImageHelper;
import com.google.ar.core.examples.java.common.view.AlphaTabsIndicator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.google.ar.core.examples.java.common.helpers.ImageHelper.createRootPath;
import static com.google.ar.core.examples.java.common.helpers.ImageHelper.getBitmapByPath;
import static com.google.ar.core.examples.java.common.helpers.ImageHelper.saveBitmapFileWithName;

public class ItemDetailActivity extends AppCompatActivity {
    private static final String TAG = ItemDetailActivity.class.getSimpleName();
    private static Long id=null;
    private static String ImagePath=null;
    private static String DepthPath=null;
    private static String RawDepthPath=null;
    private static String ConfidencePath=null;
    private static String RelDepthPath=null;
    private static String MaskPath=null;
    private static String CloudPath=null;

    private static List<Map<String,Object>> Heights=new ArrayList<>();
    private static Map<Integer,Float> HeightList=new HashMap<>();
    private static Bitmap ImageBitmap=null;
    private static Bitmap ImageForUpload=null;
    private static Bitmap DepthBitmap=null;
    private static Bitmap RawDephBitmap=null;
    private static Bitmap ConfidenceBitmap=null;
    private static Bitmap RelDepth=null;
    private static Bitmap Mask=null;
//    private static CloudFragment.PointCloud pointCloud=null;

    private static String ipv4Address = "192.168.42.101";
    private static String portNumber = "81";
    private static String postUrl = "http://" + ipv4Address + ":" + portNumber + "/getData";
    private static String getPointCloudUrl = "http://" + ipv4Address + ":" + portNumber + "/get_point_cloud";
    String rootPath=createRootPath(ItemDetailActivity.this);
    Float fx;
    Float fy;
    Float cx;
    Float cy;

    Boolean isDataComplete=false;
    private static final Pattern IP_ADDRESS
            = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                    + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                    + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                    + "|[1-9][0-9]|[0-9]))");

    private AlphaTabsIndicator alphaTabsIndicator;
    ItemDetailActivity.HomeAdapter mainAdapter;
    ListView heightListView;
    private MyAdapter simpleAdapter = null;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            id=bundle.getLong("id");
//            System.out.println("id="+id.toString());
        }
//        glView = findViewById(R.id.glview);
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        String[] PERMISSIONS = {
                Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE};
        if(ActivityCompat.checkSelfPermission(ItemDetailActivity.this,Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED){
            System.out.println("已开启联网权限");
        }else{
            ActivityCompat.requestPermissions(ItemDetailActivity.this,PERMISSIONS,1);
        }

        //获取数据
        try {
            getData();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ViewPager mViewPger = (ViewPager) findViewById(R.id.mViewPager);
        mainAdapter = new ItemDetailActivity.HomeAdapter(getSupportFragmentManager());
        mViewPger.setAdapter(mainAdapter);
        mViewPger.addOnPageChangeListener(mainAdapter);

        alphaTabsIndicator = (AlphaTabsIndicator) findViewById(R.id.alphaIndicator);
        alphaTabsIndicator.setViewPager(mViewPger);

        //以前是全部显示在一个页面，现在改成了标签页的形式
//        showImage(R.id.image,ImageBitmap);
//        showImage(R.id.depth,DepthBitmap);
//        showImage(R.id.rawdepth,RawDephBitmap);
//        showImage(R.id.confidence,ConfidenceBitmap);
//        showImage(R.id.reldepth,RelDepth);
//        showImage(R.id.mask,Mask);

        if(Heights.size()>0){
            if(Heights.size()==1){
                heightListView = (ListView) findViewById(R.id.height_list);
                heightListView.setVisibility(View.GONE);
                Map<String,Object> map=Heights.get(0);
                if((Integer)map.get("contour")==-1&&(Float)map.get("data")==-1){
                    TextView view=findViewById(R.id.height);
                    view.setText("No trees above 1.3m were identified");
                }
                else{
                    TextView view=findViewById(R.id.height);
                    view.setText(map.get("data").toString()+"m");
                }
            }
            else{
                heightListView = (ListView) findViewById(R.id.height_list);
                heightListView.setVisibility(View.VISIBLE);
                TextView view=findViewById(R.id.height);
                view.setVisibility(View.INVISIBLE);
                simpleAdapter = new MyAdapter(this, Heights, R.layout.height_list_item
                        ,new String[]{"contour","data"}
                        ,new int[]{R.id.contour_name,R.id.data}){
                    @Override
                    public long getItemId(int position) {
                        return position;
                    }
                };
                simpleAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {

                    @Override
                    public boolean setViewValue(View view, Object data,
                                                String textRepresentation) {
                        if (view instanceof TextView && data instanceof Integer) {
                            TextView iv = (TextView) view;
                            iv.setText("tree"+data);
                            return true;
                        }
                        else if(view instanceof TextView && data instanceof Float){
                            TextView iv = (TextView) view;
                            iv.setText(data+"m");
                            return true;
                        }
                        return false;
                    }
                });
                heightListView.setAdapter(simpleAdapter);
            }
            Button uploadButton=findViewById(R.id.upload_button);
            uploadButton.setText("Recalculate");
        }

//        if(pointCloud!=null){
//            renderer=new PointCloudRenderer(ItemDetailActivity.this,pointCloud);
////            glView.setRenderer(renderer);
//            Log.d(TAG,"执行了setRenderer");
//            isSetRender=true;
//        }

        MaterialToolbar toolbar = findViewById(R.id.materialToolbar);
        back(toolbar);

    }

    private void getData() throws IOException {
        DatabaseHelper TreeDBHelper = new DatabaseHelper(this, Constants.TREE_TABLE_NAME+".db",null,1);
        DatabaseHelper HeightDBHelper = new DatabaseHelper(this, Constants.HEIGHT_TABLE_NAME+".db",null,1);
        SQLiteDatabase TreeDB = TreeDBHelper.getReadableDatabase();
        SQLiteDatabase HeightDB = HeightDBHelper.getReadableDatabase();
        Cursor treeCursor = TreeDB.rawQuery("select * from "+Constants.TREE_TABLE_NAME+" where id="+id, null);
        Cursor heightCursor;
        while (treeCursor.moveToNext()){
            ImagePath=treeCursor.getString(1);
            DepthPath=treeCursor.getString(2);
            RawDepthPath=treeCursor.getString(3);
            ConfidencePath=treeCursor.getString(4);
            RelDepthPath=treeCursor.getString(5);
            MaskPath=treeCursor.getString(6);
            CloudPath=treeCursor.getString(7);

            if(Heights.size()>0){
                Heights.clear();
            }
            heightCursor = HeightDB.rawQuery("select contour_id,height from "+Constants.HEIGHT_TABLE_NAME+" where tree_id="+id, null);
            while (heightCursor.moveToNext()){
                int contour=heightCursor.getInt(0);
                Float height=heightCursor.getFloat(1);
                Map<String,Object> map = new HashMap<>();
                map.put("contour",contour);
                map.put("data",height);
                Heights.add(map);
            }
        }
        DatabaseHelper IntriDBHelper = new DatabaseHelper(this, Constants.INTRI_TABLE_NAME+".db",null,1);
        SQLiteDatabase IntriDB = IntriDBHelper.getReadableDatabase();
        Cursor intriCursor = IntriDB.rawQuery("select * from "+Constants.INTRI_TABLE_NAME, null);
        while(intriCursor.moveToNext()){
            fx=intriCursor.getFloat(1);
            fy=intriCursor.getFloat(2);
            cx=intriCursor.getFloat(3);
            cy=intriCursor.getFloat(4);
        }

        Log.d("fx",fx+"");
        Log.d("fy",fy+"");
        Log.d("cx",cx+"");
        Log.d("cy",cy+"");


        File image = new File(ImagePath);
        if(image.exists()){
            ImageBitmap = ImageHelper.getCompressedBitmap(120,160, image);
            BitmapFactory.Options options = new BitmapFactory.Options();
            ImageForUpload = BitmapFactory.decodeFile(ImagePath, options);
        }

        DepthBitmap=getBitmapByPath(DepthPath);
        RawDephBitmap=getBitmapByPath(RawDepthPath);
        ConfidenceBitmap=getBitmapByPath(ConfidencePath);

        if(!RelDepthPath.equals("empty")){
            File reldepth = new File(RelDepthPath);
            if(reldepth.exists()){
                RelDepth = ImageHelper.getCompressedBitmap(120,160, reldepth);
            }
        }else RelDepth=null;
        if(!MaskPath.equals("empty")){
            File mask = new File(MaskPath);
            if(mask.exists()){
                Mask = ImageHelper.getCompressedBitmap(120,160, mask);
            }
        }else Mask=null;

        if(DepthBitmap!=null && RawDephBitmap!=null && ConfidenceBitmap!=null&&fx!=null&&fy!=null
                &&cx!=null&&cy!=null){
            isDataComplete=true;
        }else isDataComplete=false;
    }

    public void showImage(int viewId, Bitmap bitmap){
        ImageView image=findViewById(viewId);
        if(bitmap!=null){
            image.setImageBitmap(bitmap);
            image.setBackgroundResource(0);
        }
        else image.setBackgroundResource(R.drawable.noimage);
    }

    protected void back(MaterialToolbar toolbar) {
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    public void connectServer(View v) {
        TextView responseText = findViewById(R.id.responseText);
        if (isDataComplete == false) { // This means no image is selected and thus nothing to upload.
            responseText.setText("The data is incomplete, please collect again");
            return;
        }
        responseText.setText("File upload, please wait...");

        Matcher matcher = IP_ADDRESS.matcher(ipv4Address);
        if (!matcher.matches()) {
            responseText.setText("Invalid IPv4 address");
            return;
        }

        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        //将图片和txt打包
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            ImageForUpload.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        }catch(Exception e){
            e.printStackTrace();
            return;
        }
        byte[] byteArray = stream.toByteArray();

        multipartBodyBuilder.addFormDataPart("image",id.toString() + ".jpg", RequestBody.create(byteArray,MediaType.parse("image/*jpg")));
        File depth=new File(DepthPath);
        multipartBodyBuilder.addFormDataPart("depth",depth.getName(), RequestBody.create(depth, MediaType.parse("text/plain")));
        File rawdepth=new File(RawDepthPath);
        multipartBodyBuilder.addFormDataPart("rawdepth",rawdepth.getName(), RequestBody.create(rawdepth, MediaType.parse("text/plain")));
        File confidence=new File(ConfidencePath);
        multipartBodyBuilder.addFormDataPart("confidence",confidence.getName(), RequestBody.create(confidence, MediaType.parse("text/plain")));
        multipartBodyBuilder.addFormDataPart("fx", String.valueOf(fx), RequestBody.create(fx.toString(), MediaType.parse("text/plain")));
        multipartBodyBuilder.addFormDataPart("fy", String.valueOf(fy), RequestBody.create(fy.toString(), MediaType.parse("text/plain")));
        multipartBodyBuilder.addFormDataPart("cx", String.valueOf(cx), RequestBody.create(cx.toString(), MediaType.parse("text/plain")));
        multipartBodyBuilder.addFormDataPart("cy", String.valueOf(cy), RequestBody.create(cy.toString(), MediaType.parse("text/plain")));

        RequestBody postBodyImage = multipartBodyBuilder.build();

        postRequest(postUrl, postBodyImage);
    }

    void postRequest(String postUrl, RequestBody postBody) {

//        OkHttpClient client = new OkHttpClient();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(postUrl)
                .post(postBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Cancel the post on failure.
                call.cancel();
                Log.d("FAIL", e.getMessage());

                // In order to access the TextView inside the UI thread, the code is executed inside runOnUiThread()
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView responseText = findViewById(R.id.responseText);
                        responseText.setText("Failed to connect to the server. Please try again");
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                // In order to access the TextView inside the UI thread, the code is executed inside runOnUiThread()
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView responseText = findViewById(R.id.responseText);
//                        TextView heightText = findViewById(R.id.height);
                        String responseData= null;
                        try {
                            responseData = response.body().string();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        parseJSONWithGSON(responseData);
                        responseText.setText("Complete");
                        GetPointCloud();
                    }
                });
            }
        });
    }

    private void parseJSONWithGSON(String jsonData){
        Gson gson=new Gson();
        List<ResponceData> dataList=gson.fromJson(jsonData,new TypeToken<List<ResponceData>>(){}.getType());
        for(ResponceData data:dataList){
            HeightList=data.getHeights();
            TextView view=findViewById(R.id.height);
            if(HeightList.size()>0){
                Heights.clear();
                Iterator<Map.Entry<Integer,Float>> iterator=HeightList.entrySet().iterator();
                while(iterator.hasNext()){
                    Map.Entry<Integer,Float> entry=iterator.next();
                    Map<String,Object> map=new HashMap<>();
                    map.put("contour",entry.getKey());
                    map.put("data",entry.getValue());
                    Heights.add(map);
                }
                if(Heights.size()>1){
                    simpleAdapter = new MyAdapter(this, Heights, R.layout.height_list_item
                            ,new String[]{"contour","data"}
                            ,new int[]{R.id.contour_name,R.id.data}){
                        @Override
                        public long getItemId(int position) {
                            return position;
                        }
                    };
                    simpleAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {

                        @Override
                        public boolean setViewValue(View view, Object data,
                                                    String textRepresentation) {
                            if (view instanceof TextView && data instanceof Integer) {
                                TextView iv = (TextView) view;
                                iv.setText("tree"+data);
                                return true;
                            }
                            else if(view instanceof TextView && data instanceof Float){
                                TextView iv = (TextView) view;
                                iv.setText(data+"m");
                                return true;
                            }
                            return false;
                        }
                    });
                    heightListView = (ListView) findViewById(R.id.height_list);
                    heightListView.setAdapter(simpleAdapter);
                    heightListView.setVisibility(View.VISIBLE);
                    view.setVisibility(View.INVISIBLE);
                }
                else{
                    heightListView = (ListView) findViewById(R.id.height_list);
                    heightListView.setVisibility(View.GONE);
                    view.setVisibility(View.VISIBLE);
                    view.setText(Heights.get(0).get("data")+"m");
                }
                Button uploadButton=findViewById(R.id.upload_button);
                uploadButton.setText("Recalculate");
            }
            else{
                view.setVisibility(View.VISIBLE);
                view.setText("No trees above 1.3m were identified");
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                byte[] decode = Base64.getDecoder().decode(data.getRel_depth());
                RelDepth = BitmapFactory.decodeByteArray(decode, 0, decode.length);
                decode = Base64.getDecoder().decode(data.getMask());
                Mask = BitmapFactory.decodeByteArray(decode, 0, decode.length);
            }

//            showImage(R.id.reldepth,RelDepth);
//            showImage(R.id.mask,Mask);


            saveBitmapFileWithName(RelDepth,rootPath,id.toString()+"_reldepth");
            RelDepthPath=rootPath+"/" + id.toString()+"_reldepth.jpg";
            saveBitmapFileWithName(Mask,rootPath,id.toString()+"_mask");
            MaskPath=rootPath+"/" + id.toString()+"_mask.jpg";

            AlignedDepthFragment alignedDepthFragment = (AlignedDepthFragment) mainAdapter.getItem(2);
            if (alignedDepthFragment != null) {
                alignedDepthFragment.updateData(RelDepthPath);
                alignedDepthFragment.updateUI(); // 更新UI界面
            }

            MaskFragment maskFragment=(MaskFragment)mainAdapter.getItem(3);
            if(maskFragment!=null){
                maskFragment.updateData(MaskPath);
                maskFragment.updateUI();
            }

            DatabaseHelper dbHelper = new DatabaseHelper(ItemDetailActivity.this, Constants.TREE_TABLE_NAME+".db",null,1);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("reldepth",RelDepthPath);
            values.put("mask",MaskPath);
            db.update(Constants.TREE_TABLE_NAME, values, "id=?", new String[]{id.toString()});

            dbHelper=new DatabaseHelper(ItemDetailActivity.this,Constants.HEIGHT_TABLE_NAME+".db",null,1);
            SQLiteDatabase heightDB=dbHelper.getWritableDatabase();
            heightDB.delete(Constants.HEIGHT_TABLE_NAME,"tree_id="+id,null);
            if(HeightList.size()>0){
                HeightList.forEach((key, value)->{
                    ContentValues treeValue = new ContentValues();
                    treeValue.put("tree_id",id);
                    treeValue.put("contour_id",key);
                    treeValue.put("height",value);
                    heightDB.insert(Constants.HEIGHT_TABLE_NAME, null, treeValue);
                });
            }
            else{//No trees above 1.3m were identified
                ContentValues treeValue = new ContentValues();
                treeValue.put("tree_id",id);
                treeValue.put("contour_id",-1);
                treeValue.put("height",-1);
                heightDB.insert(Constants.HEIGHT_TABLE_NAME, null, treeValue);
            }
        }
    }

    private void GetPointCloud(){
        OkHttpClient client=new OkHttpClient();
        Request request = new Request.Builder()
                .url(getPointCloudUrl)
                .build();
        // 开始下载PLY文件
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "下载PLY文件失败", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 检查请求是否成功
                if (!response.isSuccessful()) {
                    throw new IOException("请求失败：" + response);
                }

                // 将数据流写入本地文件
                CloudPath=rootPath+"/"+id.toString()+"_cloud.ply";
                File outputFile = new File(CloudPath);
                InputStream inputStream = response.body().byteStream();
                FileOutputStream fileOutputStream = new FileOutputStream(outputFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }

                fileOutputStream.close();
                inputStream.close();

                DatabaseHelper dbHelper = new DatabaseHelper(ItemDetailActivity.this,
                        Constants.TREE_TABLE_NAME+".db",null,1);
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("cloud",CloudPath);
                db.update(Constants.TREE_TABLE_NAME, values, "id="+id,null);

                CloudFragment cloudFragment=(CloudFragment) mainAdapter.getItem(4);
                if(cloudFragment!=null){
                    cloudFragment.updateData(CloudPath);
                    cloudFragment.updateUI();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        alphaTabsIndicator.JumpToMaskFragment();
                    }
                });
//                File cloud = new File(CloudPath);
//                if(cloud.exists()){
//                    pointCloud = readPointCloud(CloudPath);
//                    renderer=new PointCloudRenderer(ItemDetailActivity.this,pointCloud);
//                    if(isSetRender){
//                        glView.requestRender();
//                        Log.d(TAG,"执行了requestRender");
//                    }
//                    else{
//                        glView.setRenderer(renderer);
//                        Log.d(TAG,"执行了setRenderer");
//                        isSetRender=true;
//                    }
//                }

            }
        });
    }



    private class HomeAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener {

        private List<Fragment> fragments = new ArrayList<>();
        private String[] titles = {"RGB", "Abs Depth", "Ali Depth","Mask","3D Point Cloud"};

        public HomeAdapter(FragmentManager fm) {
            super(fm);
            Bundle bundle = new Bundle();
            bundle.putLong("id",id);

            Fragment rgbFrag=RGBFragment.newInstance(titles[0]);
            rgbFrag.setArguments(bundle);
            fragments.add(rgbFrag);

            Fragment absDepthFrag=AbsDepthFragment.newInstance(titles[1]);
            absDepthFrag.setArguments(bundle);
            fragments.add(absDepthFrag);

            Fragment alignedDepthFrag=AlignedDepthFragment.newInstance(titles[2]);
            alignedDepthFrag.setArguments(bundle);
            fragments.add(alignedDepthFrag);

            Fragment maskFrag= MaskFragment.newInstance(titles[3]);
            maskFrag.setArguments(bundle);
            fragments.add(maskFrag);

            Fragment cloudFrag= CloudFragment.newInstance(titles[4]);
            cloudFrag.setArguments(bundle);
            fragments.add(cloudFrag);
        }

        @Override
        public Fragment getItem(int position) {
            return fragments.get(position);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            if (0 == position) {
                alphaTabsIndicator.getCurrentItemView().removeShow();
            } else if (4 == position) {
                alphaTabsIndicator.getCurrentItemView().removeShow();
            } else if (5 == position) {
                alphaTabsIndicator.removeAllBadge();
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    }
}
