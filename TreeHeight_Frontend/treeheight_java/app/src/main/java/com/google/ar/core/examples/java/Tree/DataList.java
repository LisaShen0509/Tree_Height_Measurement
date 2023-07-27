package com.google.ar.core.examples.java.Tree;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.ar.core.examples.java.common.ExportData;
import com.google.ar.core.examples.java.common.MyAdapter;
import com.google.ar.core.examples.java.common.ResponceData;
import com.google.ar.core.examples.java.common.UploadData;
import com.google.ar.core.examples.java.common.helpers.Constants;
import com.google.ar.core.examples.java.common.helpers.DatabaseHelper;
import com.google.ar.core.examples.java.common.helpers.ImageHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jxl.Workbook;
import jxl.format.Colour;
import jxl.write.Label;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
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
import static com.google.ar.core.examples.java.common.helpers.ImageHelper.isSdCardAvailable;
import static com.google.ar.core.examples.java.common.helpers.ImageHelper.saveBitmapFileWithName;

public class DataList extends Activity {
    private ListView listView = null;
    private List<Map<String,Object>> list = new ArrayList<>();
    private MyAdapter simpleAdapter = null;
    private boolean isMultiSelect = false; // 是否处于多选模式
    private List<Long> selectedSet = new ArrayList<Long>(); // 记录已选择的项的id
    Button editButton;//进入多选模式的按钮
    CheckBox selectAll;//全选按钮
    boolean isFromItem=false;//监听点击是否来自列表项目
    LinearLayout multiToolBar;//批量处理工具栏
    Button multiDelete;
    Button multiUpload;
    Button cameraButton;

    private static String ipv4Address = "192.168.42.101";
    private static String portNumber = "81";
    private static String postUrl = "http://" + ipv4Address + ":" + portNumber + "/getData";
    private static String getPointCloudUrl = "http://" + ipv4Address + ":" + portNumber + "/get_point_cloud";
    private static final Pattern IP_ADDRESS
            = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                    + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                    + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                    + "|[1-9][0-9]|[0-9]))");
    String rootPath=createRootPath(DataList.this);
    Boolean isDataComplete=false;

    LinearLayout progressLayout=null;
    ProgressBar progressBar=null;
    TextView progessNum=null;
    private int nowProgress=0;
    private int maxProgress=0;

    private Map<Integer,Boolean> isCheckedMap=new HashMap<Integer,Boolean>();

    Handler handler = null;
    Float fx;
    Float fy;
    Float cx;
    Float cy;

    private int scrolledX = 0;
    private int scrolledY = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dataset);
        listView = (ListView) findViewById(android.R.id.list);
        cameraButton=findViewById(R.id.camera_button);
        editButton=findViewById(R.id.edit_button);
        multiToolBar=findViewById(R.id.multi_toolbar);
        multiDelete=findViewById(R.id.multi_delete_botton);
        multiUpload=findViewById(R.id.multi_upload_button);
        selectAll=findViewById(R.id.select_all);
        progressLayout=findViewById(R.id.progress_layout);
        progressBar=findViewById(R.id.progressBar);
        progessNum=findViewById(R.id.progress_num);
        handler=new Handler();

        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (ActivityCompat.checkSelfPermission(DataList.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            System.out.println("已开启写入外存权限");
        } else {
            ActivityCompat.requestPermissions(DataList.this, PERMISSIONS_STORAGE, 1);
        }

        try {
            list=getdata(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(list.size()>0){
            TextView emptyMessage=findViewById(R.id.empty_message);
            emptyMessage.setText("");
            simpleAdapter = new MyAdapter(this, list, R.layout.list_item
                    ,new String[]{"num","Image","Depth","Height","check"}
                    ,new int[]{R.id.data_id,R.id.image,R.id.depth,R.id.height,R.id.check_box}){
                @Override
                public long getItemId(int position) {
                    return position;
                }
            };
            simpleAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {

                @Override
                public boolean setViewValue(View view, Object data,
                                            String textRepresentation) {
                    if (view instanceof ImageView && data instanceof Bitmap) {
                        ImageView iv = (ImageView) view;
                        iv.setImageBitmap((Bitmap) data);
                        return true;
                    }
                    else if (view instanceof TextView && data instanceof String) {
                        TextView iv = (TextView) view;
                        iv.setText((String)data);
                        if(((String)data).equals("Not calculated")){
                            iv.setTextColor(Color.RED);
                        }
                        else{
                            iv.setTextColor(Color.BLACK);
                        }
                        return true;
                    }
                    else if(view instanceof CheckBox && data instanceof Boolean){
                        CheckBox iv=(CheckBox) view;
                        iv.setChecked((boolean)data);
                        // 添加 CheckBox 的点击事件
                        iv.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int pos = v.getId(); // 获取 CheckBox 的位置
                                listView.performItemClick(view, pos, simpleAdapter.getItemId(pos)); // 触发 onItemClick 事件
                                iv.setTag(pos); // 将 CheckBox 的位置设置为 tag，以便在 onClick 中使用
                            }
                        });

                        return true;
                    }
                    return false;
                }
            });

            listView.setAdapter(simpleAdapter);
//            simpleAdapter.notifyDataSetChanged();
            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isMultiSelect = !isMultiSelect; // 切换多选模式状态
                    if (isMultiSelect) {    //进入多选模式
//                        selectedSet.clear(); // 清空已选择的项
                        editButton.setText("Cancel"); // 将按钮文本改为“Cancel”
                        selectAll.setVisibility(View.VISIBLE);
                        multiToolBar.setVisibility(View.VISIBLE);
                        cameraButton.setVisibility(View.GONE);

                        // 显示CheckBox视图
                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View childView = listView.getChildAt(i);
                            CheckBox cb = childView.findViewById(R.id.check_box);
                            cb.setVisibility(View.VISIBLE);
                        }
                    }
                    else {  //退出多选模式
                        editButton.setText("Manage");
                        selectAll.setChecked(false);
                        selectAll.setVisibility(View.GONE);
                        multiToolBar.setVisibility(View.GONE);
                        cameraButton.setVisibility(View.VISIBLE);
                        // 隐藏CheckBox视图
                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View childView = listView.getChildAt(i);
                            CheckBox cb = childView.findViewById(R.id.check_box);
                            cb.setChecked(false); // 多选模式结束时，将所有项的CheckBox状态重置为未选中
                            cb.setVisibility(View.INVISIBLE);
                        }
                    }
                }
            });
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    ListView listView = (ListView)parent;
                    HashMap<String, Object> map = (HashMap<String, Object>) listView.getItemAtPosition(position);
                    Long image_id= (Long) map.get("id");
                    if (isMultiSelect) { // 如果处于多选模式
                        Log.d("TAG","处于多选模式");
                        CheckBox cb = view.findViewById(R.id.check_box);
                        boolean isChecked = cb.isChecked();
                        cb.setChecked(!isChecked); // 反转CheckBox选中状态
                        if (isChecked) {
                            System.out.println("delete id:"+String.valueOf(image_id));
                            selectedSet.remove(image_id); // 如果之前已选中该项，则从selectedSet中移除
                            list.get(position).put("check",false);
                            simpleAdapter.setmList(list);
                            Log.d("selectedNum", String.valueOf(selectedSet.size()));
                            if(selectAll.isChecked()){
                                isFromItem=true;
                                selectAll.setChecked(false);
                            }
                        } else {
                            System.out.println("add id:"+String.valueOf(image_id));
                            selectedSet.add(image_id); // 否则，将其位置添加到selectedSet中
                            list.get(position).put("check",true);
                            simpleAdapter.setmList(list);
                            Log.d("selectedNum", String.valueOf(selectedSet.size()));
                            if(selectedSet.size()==list.size() && !selectAll.isChecked()){
                                isFromItem=true;
                                selectAll.setChecked(true);
                            }
                        }
                    } else { // 如果不处于多选模式，则跳转到详情页
                        Log.d("TAG","处于单选模式");
                        onListItemClick(image_id);
                    }
                }
            });
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                /**
                 * 滚动状态改变时调用
                 */
                @Override
                public void onScroll(AbsListView view, int firstVisibleItem,
                                     int visibleItemCount, int totalItemCount) {
                }
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                        try {
                            scrolledX = view.getFirstVisiblePosition();
                            Log.i("scroll X", String.valueOf(scrolledX));
                            scrolledY = view.getChildAt(0).getTop();
                            Log.i("scroll Y", String.valueOf(scrolledY));
                        } catch (Exception e) {
                        }
                    }
                }
                });
            selectAll.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    //当监听来源为点击item改变maincbk状态时不在监听改变，防止死循环
                    if(isFromItem){
                        isFromItem=false;
                        Log.d("全选checkbox","此时不触发");
                        return;
                    }
                    if(b){//全选
                        selectedSet.clear(); // 先清空已选择的项
                        for (int i = 0; i < listView.getChildCount(); i++) {
//                            System.out.println("i="+i);
                            View childView = listView.getChildAt(i);
                            CheckBox cb = childView.findViewById(R.id.check_box);
                            cb.setChecked(true);
                        }
                        for(int i=0;i<list.size();i++){
                            Map<String,Object> ItemData=list.get(i);
                            selectedSet.add((Long)ItemData.get("id"));
                            list.get(i).put("check",true);
                        }
                        Log.d("selectedNum", String.valueOf(selectedSet.size()));
                    }
                    else{//Cancel全选
                        selectedSet.clear(); // 先清空已选择的项
                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View childView = listView.getChildAt(i);
                            CheckBox cb = childView.findViewById(R.id.check_box);
                            cb.setChecked(false);
                        }
                        for(int i=0;i<list.size();i++){
                            list.get(i).put("check",false);
                        }
                        Log.d("selectedNum", String.valueOf(selectedSet.size()));
                    }
                }
            });
        }

    }

    public void onBack() {
        listView.setSelectionFromTop(scrolledX, scrolledY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        onCreate(null);
        onBack();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


    void onListItemClick(Long id) {
        Intent intent = null;
        intent = new Intent(DataList.this, ItemDetailActivity.class);
        intent.putExtra("id",id);
        this.startActivity(intent);
    }

    private List<Map<String,Object>> getdata(Boolean isDeleteAll) throws IOException {
        DatabaseHelper TreeDBHelper = new DatabaseHelper(this, Constants.TREE_TABLE_NAME+".db",null,1);
        DatabaseHelper HeightDBHelper = new DatabaseHelper(this, Constants.HEIGHT_TABLE_NAME+".db",null,1);
//        SQLiteDatabase db = TreeDBHelper.getWritableDatabase();
//        ContentValues values = new ContentValues();
//        String ImageName="1681449816167";
//        values.put("id", Long.valueOf(ImageName));
//        values.put("image", rootPath + "/" + ImageName + ".jpg");
//        values.put("depth", rootPath + "/" + ImageName + "_depthdata.txt");
//        values.put("rawdepth", rootPath + "/" + ImageName + "_rawdepthdata.txt");
//        values.put("confidence", rootPath + "/" + ImageName + "_confidencedata.txt");
//        values.put("reldepth", "empty");
//        values.put("mask", "empty");
//        values.put("cloud", "empty");
//        try {
//            db.insert(Constants.TREE_TABLE_NAME, null, values);
//        } catch (Exception e) {
//            e.printStackTrace();
//            Log.e("Error", "保存数据到数据库失败");
//        }
        SQLiteDatabase TreeDB = TreeDBHelper.getReadableDatabase();
        SQLiteDatabase HeightDB = HeightDBHelper.getReadableDatabase();
        Cursor treeCursor = TreeDB.rawQuery("select * from "+Constants.TREE_TABLE_NAME, null);
        Cursor heightCursor;
        List<Boolean> checkList=new ArrayList<Boolean>();
        if(!isDeleteAll&&!list.isEmpty()){
            for(int i=0;i<list.size();i++){
                checkList.add((Boolean) list.get(i).get("check"));
            }
        }
        list.clear();
        Integer num=1;
        while (treeCursor.moveToNext()) {
            Long id=treeCursor.getLong(0);
            String data = treeCursor.getString(1);
            File image = new File(data);
            Bitmap imageBitmap=null;
            if(image.exists()){
                imageBitmap = ImageHelper.getCompressedBitmap(120,160, image);
            }

            data = treeCursor.getString(2);
            Bitmap depthBitmap=getBitmapByPath(data);

            List<Float> heights = new ArrayList<Float>();
            heightCursor = HeightDB.rawQuery("select height from "+Constants.HEIGHT_TABLE_NAME+" where tree_id="+id, null);
            while (heightCursor.moveToNext()){
                Float h=heightCursor.getFloat(0);
                heights.add(h);
            }

            Map<String,Object> map = new HashMap<>();
            map.put("num",num);
            map.put("id",id);
            if(imageBitmap!=null) {
                map.put("Image",imageBitmap);
            }
            if(depthBitmap!=null) {
                map.put("Depth",depthBitmap);
            }
            if(heights==null || heights.size()==0){
                map.put("Height","Not calculated");
            }
            else if(heights.size()==1){
                if(heights.get(0)==-1){
                    map.put("Height","No tree");
                }
                else{
                    map.put("Height",heights.get(0).toString()+"m");
                }
            }
            else if(heights.size()>1){
                map.put("Height","Include "+String.valueOf(heights.size())+" data");
            }
            if(!checkList.isEmpty()&&checkList.size()>num-1){
                map.put("check",checkList.get(num-1));
            }
            else{
                map.put("check",false);
            }
            list.add(map);
            num+=1;
        }
        if(list.isEmpty()){
            System.out.println("list为空，手动插入数据");
            SQLiteDatabase db = TreeDBHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            String ImageName="1680935393752";
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
            values = new ContentValues();
            ImageName="1681451985902";
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
            values = new ContentValues();
            ImageName="1681450501264";
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
            num=1;
            while (treeCursor.moveToNext()) {
                Long id=treeCursor.getLong(0);
                String data = treeCursor.getString(1);
                File image = new File(data);
                Bitmap imageBitmap=null;
                if(image.exists()){
                    imageBitmap = ImageHelper.getCompressedBitmap(120,160, image);
                }

                data = treeCursor.getString(2);
                Bitmap depthBitmap=getBitmapByPath(data);

                List<Float> heights = new ArrayList<Float>();
                heightCursor = HeightDB.rawQuery("select height from "+Constants.HEIGHT_TABLE_NAME+" where tree_id="+id, null);
                while (heightCursor.moveToNext()){
                    Float h=heightCursor.getFloat(0);
                    heights.add(h);
                }

                Map<String,Object> map = new HashMap<>();
                map.put("num",num);
                map.put("id",id);
                if(imageBitmap!=null) {
                    map.put("Image",imageBitmap);
                }
                if(depthBitmap!=null) {
                    map.put("Depth",depthBitmap);
                }
                if(heights==null || heights.size()==0){
                    map.put("Height","Not calculated");
                }
                else if(heights.size()==1){
                    if(heights.get(0)==-1){
                        map.put("Height","No tree");
                    }
                    else{
                        map.put("Height",heights.get(0).toString()+"m");
                    }
                }
                else if(heights.size()>1){
                    map.put("Height","Include "+String.valueOf(heights.size())+" data");
                }
                if(!checkList.isEmpty()&&checkList.size()>num-1){
                    map.put("check",checkList.get(num-1));
                }
                else{
                    map.put("check",false);
                }
                list.add(map);
                num+=1;
            }
        }
        return list;
    }

    public void GoSecond(View v){
        Intent intent = new Intent(this,MainActivity.class);
        startActivity(intent);
    }

    //批量上传
    public void connectServer(View v) {
        nowProgress=0;
        maxProgress=selectedSet.size();
        if(nowProgress==maxProgress){
            Toast.makeText(DataList.this, "Please select data!", Toast.LENGTH_SHORT).show();
        }
        else start(progressBar,progessNum,"upload");
    }
    //批量删除计算结果
    public void deleteResult(View v) {
        Integer num=selectedSet.size();
        if(num==0){
            Toast.makeText(DataList.this, "Please select data!", Toast.LENGTH_SHORT).show();
        }
        else{
            new AlertDialog.Builder(this)
                    .setTitle("Confirm deletion")
                    .setMessage("Are you sure you want to delete these "+num+" entries?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            nowProgress=0;
                            maxProgress=selectedSet.size();
                            start(progressBar,progessNum,"deleteResult");
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create()
                    .show();
        }
    }
    //删除数据
    public void deleteData(View v){
        Integer num=selectedSet.size();
        if(num==0){
            Toast.makeText(DataList.this, "Please select data!", Toast.LENGTH_SHORT).show();
        }
        else{
            new AlertDialog.Builder(this)
                    .setTitle("Confirm deletion")
                    .setMessage("Are you sure you want to delete these "+num+" entries?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            nowProgress=0;
                            maxProgress=selectedSet.size();
                            start(progressBar,progessNum,"deleteData");
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create()
                    .show();
        }
    }

    void postRequest(String postUrl, RequestBody postBody,Long id) throws IOException {

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();


        //异步请求
        //        Request request = new Request.Builder()
//                .url(postUrl)
//                .post(postBody)
//                .build();
//        client.newCall(request).enqueue(new Callback() {
//            @Override
//            public void onFailure(Call call, IOException e) {
//                // Cancel the post on failure.
//                call.cancel();
//                Log.d("FAIL", e.getMessage());
//
//                // In order to access the TextView inside the UI thread, the code is executed inside runOnUiThread()
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        Toast.makeText(DataList.this, "连接到服务器失败，请重试", Toast.LENGTH_SHORT).show();
//                        return;
//                    }
//                });
//            }
//
//            @Override
//            public void onResponse(Call call, final Response response) throws IOException {
//                String responseData= null;
//                try {
//                    responseData = response.body().string();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                parseJSONWithGSON(responseData,id);
//                GetPointCloud(id);
//
//            }
//        });

        //同步请求
        Response response = null;
        try{
            Request request = new Request.Builder()
                    .url(postUrl)
                    .post(postBody)
                    .build();
            response = client.newCall(request).execute();
        }catch(IOException e){
            e.printStackTrace();
        }

        if(response==null){
            //连接异常处理
        }else {
            String responseData = response.body().string();
            parseJSONWithGSON(responseData,id);
            GetPointCloud(id);
        }

    }

    private void parseJSONWithGSON(String jsonData,Long id){
        Gson gson=new Gson();
        List<ResponceData> dataList= null;
        dataList = gson.fromJson(jsonData,new TypeToken<List<ResponceData>>(){}.getType());
        for(ResponceData data:dataList){
            Map<Integer,Float> Heights=data.getHeights();
            Bitmap RelDepth=null;
            Bitmap Mask=null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                byte[] decode = Base64.getDecoder().decode(data.getRel_depth());
                RelDepth = BitmapFactory.decodeByteArray(decode, 0, decode.length);
                decode = Base64.getDecoder().decode(data.getMask());
                Mask = BitmapFactory.decodeByteArray(decode, 0, decode.length);
            }

            saveBitmapFileWithName(RelDepth,rootPath,id.toString()+"_reldepth");
            String RelDepthPath=rootPath+"/" + id.toString()+"_reldepth.jpg";
            saveBitmapFileWithName(Mask,rootPath,id.toString()+"_mask");
            String MaskPath=rootPath+"/" + id.toString()+"_mask.jpg";

            DatabaseHelper dbHelper = new DatabaseHelper(DataList.this, Constants.TREE_TABLE_NAME+".db",null,1);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("reldepth",RelDepthPath);
            values.put("mask",MaskPath);
            db.update(Constants.TREE_TABLE_NAME, values, "id="+id,null);

            dbHelper=new DatabaseHelper(DataList.this,Constants.HEIGHT_TABLE_NAME+".db",null,1);
            SQLiteDatabase heightDB=dbHelper.getWritableDatabase();
            heightDB.delete(Constants.HEIGHT_TABLE_NAME,"tree_id="+id,null);
            if(Heights.size()>0){
                Heights.forEach((key, value)->{
                    ContentValues treeValue = new ContentValues();
                    treeValue.put("tree_id",id);
                    treeValue.put("contour_id",key);
                    treeValue.put("height",value);
                    heightDB.insert(Constants.HEIGHT_TABLE_NAME, null, treeValue);
                });
            }
            else{//未识别出1.3m以上树木
                ContentValues treeValue = new ContentValues();
                treeValue.put("tree_id",id);
                treeValue.put("contour_id",-1);
                treeValue.put("height",-1);
                heightDB.insert(Constants.HEIGHT_TABLE_NAME, null, treeValue);
            }
        }
    }

    private Boolean GetPointCloud(Long id){
        OkHttpClient client=new OkHttpClient();
        Request request = new Request.Builder()
                .url(getPointCloudUrl)
                .build();
        // 开始下载PLY文件
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("TAG", "下载PLY文件失败", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 检查请求是否成功
                if (!response.isSuccessful()) {
                    throw new IOException("请求失败：" + response);
                }

                // 将数据流写入本地文件
                String CloudPath=rootPath+"/"+id.toString()+"_cloud.ply";
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

                DatabaseHelper TreeDBHelper = new DatabaseHelper(DataList.this, Constants.TREE_TABLE_NAME+".db",null,1);
                SQLiteDatabase db = TreeDBHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("cloud",CloudPath);
                db.update(Constants.TREE_TABLE_NAME, values, "id="+id, null);

            }
        });
        return true;
    }

    public void start(final ProgressBar progressBar, final TextView textView,String type) {
        new Thread(){
            public void run(){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressLayout.setVisibility(View.VISIBLE);
                        progressBar.setMax(maxProgress);
                        progressBar.setProgress(nowProgress);
                        textView.setText(nowProgress + "/" + maxProgress);
                    }
                });
                ExecutorService threadPool = Executors.newCachedThreadPool();
                if(type.equals("upload")){
                    while (nowProgress < maxProgress) {
                        Long tmpid=selectedSet.get(nowProgress);
                        threadPool.submit(new Runnable() {
                            @Override
                            public void run() {
                                isDataComplete=false;
                                UploadData uploadData=null;
                                try {
                                    uploadData=getDataForUpload(tmpid);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                if (isDataComplete == false) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(DataList.this, tmpid+"数据不全", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                                else{
                                    MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
                                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                    try {
                                        uploadData.getImage().compress(Bitmap.CompressFormat.JPEG, 100, stream);
                                    }catch(Exception e){
                                        e.printStackTrace();
                                        return;
                                    }
                                    byte[] byteArray = stream.toByteArray();
                                    multipartBodyBuilder.addFormDataPart("image",tmpid.toString() + ".jpg",
                                            RequestBody.create(byteArray,MediaType.parse("image/*jpg")));
                                    multipartBodyBuilder.addFormDataPart("depth",uploadData.getDepth().getName(),
                                            RequestBody.create(uploadData.getDepth(), MediaType.parse("text/plain")));
                                    multipartBodyBuilder.addFormDataPart("rawdepth",uploadData.getRawdepth().getName(),
                                            RequestBody.create(uploadData.getRawdepth(), MediaType.parse("text/plain")));
                                    multipartBodyBuilder.addFormDataPart("confidence",uploadData.getConfidence().getName(),
                                            RequestBody.create(uploadData.getConfidence(), MediaType.parse("text/plain")));
                                    multipartBodyBuilder.addFormDataPart("fx", String.valueOf(fx), RequestBody.create(fx.toString(), MediaType.parse("text/plain")));
                                    multipartBodyBuilder.addFormDataPart("fy", String.valueOf(fy), RequestBody.create(fy.toString(), MediaType.parse("text/plain")));
                                    multipartBodyBuilder.addFormDataPart("cx", String.valueOf(cx), RequestBody.create(cx.toString(), MediaType.parse("text/plain")));
                                    multipartBodyBuilder.addFormDataPart("cy", String.valueOf(cy), RequestBody.create(cy.toString(), MediaType.parse("text/plain")));
                                    RequestBody postBodyImage = multipartBodyBuilder.build();
                                    try {
                                        postRequest(postUrl, postBodyImage,tmpid);
                                        System.out.println("执行了一次postRequest");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            list=getdata(false);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        MyAdapter newAdapter=(MyAdapter)listView.getAdapter();
                                        newAdapter.myNotifyDataSetChanged(list);
                                        System.out.println("刷新了listView");
                                        progressBar.setProgress(nowProgress);
                                        textView.setText(nowProgress + "/" + maxProgress);
                                    }
                                });
                            }
                        });
                        nowProgress++;
                        try {
                            Thread.sleep(8000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                else if(type.equals("deleteResult")){
                    while (nowProgress < maxProgress) {
                        Long tmpid=selectedSet.get(nowProgress);
                        threadPool.submit(new Runnable() {
                            @Override
                            public void run() {
                                DatabaseHelper dbHelper = new DatabaseHelper(DataList.this, Constants.TREE_TABLE_NAME+".db",null,1);
                                SQLiteDatabase db = dbHelper.getReadableDatabase();
                                //先删除文件再将路径置空
                                Cursor treeCursor = db.rawQuery("select * from "+Constants.TREE_TABLE_NAME+" where id="+tmpid, null);
                                Cursor heightCursor;
                                while (treeCursor.moveToNext()){
                                    String RelDepthPath=treeCursor.getString(5);
                                    String MaskPath=treeCursor.getString(6);
                                    String CloudPath=treeCursor.getString(7);
                                    if(!RelDepthPath.equals("empty")){
                                        deleteFile(RelDepthPath);
                                    }
                                    if(!MaskPath.equals("empty")){
                                        deleteFile(MaskPath);
                                    }
                                    if(!CloudPath.equals("empty")){
                                        deleteFile(CloudPath);
                                    }
                                }
                                db = dbHelper.getWritableDatabase();
                                ContentValues values = new ContentValues();
                                values.put("reldepth","empty");
                                values.put("mask","empty");
                                values.put("cloud","empty");
                                db.update(Constants.TREE_TABLE_NAME, values, "id="+tmpid,null);

                                dbHelper=new DatabaseHelper(DataList.this,Constants.HEIGHT_TABLE_NAME+".db",null,1);
                                SQLiteDatabase heightDB=dbHelper.getWritableDatabase();
                                heightDB.delete(Constants.HEIGHT_TABLE_NAME,"tree_id="+tmpid,null);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            list=getdata(false);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        MyAdapter newAdapter=(MyAdapter)listView.getAdapter();
                                        newAdapter.myNotifyDataSetChanged(list);
                                        progressBar.setProgress(nowProgress);
                                        textView.setText(nowProgress + "/" + maxProgress);
                                    }
                                });
                            }
                        });
                        nowProgress++;
                    }
                }
                else if(type.equals("deleteData")){
                    while (nowProgress < maxProgress) {
                        Long tmpid=selectedSet.get(nowProgress);
                        threadPool.submit(new Runnable() {
                            @Override
                            public void run() {
                                DatabaseHelper dbHelper = new DatabaseHelper(DataList.this, Constants.TREE_TABLE_NAME+".db",null,1);
                                SQLiteDatabase db = dbHelper.getReadableDatabase();
                                //先删除文件再删除数据
                                Cursor treeCursor = db.rawQuery("select * from "+Constants.TREE_TABLE_NAME+" where id="+tmpid, null);
                                Cursor heightCursor;
                                while (treeCursor.moveToNext()){
                                    String ImagePath=treeCursor.getString(1);
                                    String DepthPath=treeCursor.getString(2);
                                    String RawDepthPath=treeCursor.getString(3);
                                    String ConfidencePath=treeCursor.getString(4);
                                    String RelDepthPath=treeCursor.getString(5);
                                    String MaskPath=treeCursor.getString(6);
                                    String CloudPath=treeCursor.getString(7);
                                    deleteFile(ImagePath);
                                    deleteFile(DepthPath);
                                    deleteFile(RawDepthPath);
                                    deleteFile(ConfidencePath);
                                    if(!RelDepthPath.equals("empty")){
                                        deleteFile(RelDepthPath);
                                    }
                                    if(!MaskPath.equals("empty")){
                                        deleteFile(MaskPath);
                                    }
                                    if(!CloudPath.equals("empty")){
                                        deleteFile(CloudPath);
                                    }
                                }
                                db = dbHelper.getWritableDatabase();
                                db.delete(Constants.TREE_TABLE_NAME, "id="+tmpid,null);

                                dbHelper=new DatabaseHelper(DataList.this,Constants.HEIGHT_TABLE_NAME+".db",null,1);
                                SQLiteDatabase heightDB=dbHelper.getWritableDatabase();
                                heightDB.delete(Constants.HEIGHT_TABLE_NAME,"tree_id="+tmpid,null);
                                selectedSet.remove(tmpid);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {

                                            list=getdata(true);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        MyAdapter newAdapter=(MyAdapter)listView.getAdapter();
                                        newAdapter.myNotifyDataSetChanged(list);
                                        progressBar.setProgress(nowProgress);
                                        textView.setText(nowProgress + "/" + maxProgress);
                                    }
                                });
                            }
                        });
                        nowProgress++;
                    }
                }
                threadPool.shutdown();
                try{
                    threadPool.awaitTermination(Long.MAX_VALUE,TimeUnit.NANOSECONDS);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressLayout.setVisibility(View.GONE);
                    }
                });
            };
        }.start();
    }

    public boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.isFile() && file.exists()) {
            return file.delete();
        }
        return false;
    }

    private UploadData getDataForUpload(Long id) throws IOException {
        DatabaseHelper TreeDBHelper = new DatabaseHelper(this, Constants.TREE_TABLE_NAME+".db",null,1);
        DatabaseHelper HeightDBHelper = new DatabaseHelper(this, Constants.HEIGHT_TABLE_NAME+".db",null,1);
        SQLiteDatabase TreeDB = TreeDBHelper.getReadableDatabase();
        Cursor treeCursor = TreeDB.rawQuery("select * from "+Constants.TREE_TABLE_NAME+" where id="+id, null);
        String ImagePath=null;
        String DepthPath=null;
        String RawDepthPath=null;
        String ConfidencePath=null;
        while (treeCursor.moveToNext()){
            ImagePath=treeCursor.getString(1);
            DepthPath=treeCursor.getString(2);
            RawDepthPath=treeCursor.getString(3);
            ConfidencePath=treeCursor.getString(4);
        }

        Bitmap ImageForUpload=null;
        File image = new File(ImagePath);
        if(image.exists()){
            BitmapFactory.Options options = new BitmapFactory.Options();
            ImageForUpload = BitmapFactory.decodeFile(ImagePath, options);
        }

        File depth=new File(DepthPath);
        File rawdepth=new File(RawDepthPath);
        File confidence=new File(ConfidencePath);

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

        if(ImageForUpload!=null && depth!=null && rawdepth!=null && confidence!=null&&fx!=null&&fy!=null
                &&cx!=null&&cy!=null){
            isDataComplete=true;
            UploadData uploadData=new UploadData(ImageForUpload,depth,rawdepth,confidence);
            return uploadData;
        }else {
            isDataComplete=false;
            return null;
        }

    }

    public  void ExportData(View v) throws Exception {
        DatabaseHelper HeightDBHelper = new DatabaseHelper(this, Constants.HEIGHT_TABLE_NAME+".db",null,1);
        SQLiteDatabase HeightDB = HeightDBHelper.getReadableDatabase();
        Cursor heightCursor;
        List<ExportData> dataList=new ArrayList<>();
        if(selectedSet.size()>0){
            for(int i=0;i<selectedSet.size();i++){
                heightCursor = HeightDB.rawQuery("select * from "+Constants.HEIGHT_TABLE_NAME+" where tree_id="+selectedSet.get(i).toString(), null );
                while (heightCursor.moveToNext()){
                    Integer contourId=heightCursor.getInt(2);
                    if(contourId!=-1){
                        Long id=heightCursor.getLong(0);
                        Long treeId=heightCursor.getLong(1);
                        Float height=heightCursor.getFloat(3);
                        ExportData data=new ExportData(id,treeId,contourId,height);
                        dataList.add(data);
                    }
                }
            }
            Date date = new Date(System.currentTimeMillis());
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
            String fileName="treeHeights_"+String.valueOf(simpleDateFormat.format(date))+".xls";
            writeExcel(dataList,fileName);
        }
        else{
            Toast.makeText(DataList.this, "Please select data!", Toast.LENGTH_SHORT).show();
        }
    }

    public void writeExcel( List<ExportData> exportOrder, String fileName) throws Exception {
        if (!isSdCardAvailable()) {
            Toast.makeText(DataList.this, "SD card is not available", Toast.LENGTH_SHORT).show();
            return;
        }

        //要导出的字段
        String[] title = { "id", "tree_id","contour_id","height"};
        File file;
        String rootPath=createRootPath(this);
        file = new File(rootPath+"/"+ fileName);
        // 创建Excel工作表
        WritableWorkbook wwb;
        OutputStream os = new FileOutputStream(file);
        wwb = Workbook.createWorkbook(os);
        // 添加第一个工作表并设置第一个Sheet的名字
        WritableSheet sheet = wwb.createSheet("sheet1", 0);
        Label label;
        for (int i = 0; i < title.length; i++) {
            // Label(x,y,z) 代表单元格的第x+1列，第y+1行, 内容z
            // 在Label对象的子对象中指明单元格的位置和内容
            label = new Label(i, 0, title[i], getHeader());
            // 将定义好的单元格添加到工作表中
            sheet.addCell(label);
        }
        //exportOrder是要导出的对应字段值
        for (int i = 0; i < exportOrder.size(); i++) {
            ExportData order = exportOrder.get(i);
            Label id= new Label(0, i + 1, order.getId().toString());
            Label treeId= new Label(1, i + 1, order.getTreeId().toString());
            Label contourId= new Label(2, i + 1, order.getContourId().toString());
            Label Height= new Label(3, i + 1, order.getHeight().toString());

            sheet.addCell(id);
            sheet.addCell(treeId);
            sheet.addCell(contourId);
            sheet.addCell(Height);
        }
        // 写入数据
        wwb.write();
        // 关闭文件
        wwb.close();

        Toast.makeText(DataList.this, "The data has been exported to "+rootPath+"/"+ fileName, Toast.LENGTH_SHORT).show();
    }

    public static WritableCellFormat getHeader() {
        WritableFont font = new WritableFont(WritableFont.TIMES, 10,
                WritableFont.BOLD);// 定义字体
        try {
            font.setColour(Colour.BLUE);// 蓝色字体
        } catch (WriteException e1) {
            e1.printStackTrace();
        }
        WritableCellFormat format = new WritableCellFormat(font);
        try {
            format.setAlignment(jxl.format.Alignment.CENTRE);// 左右居中
            format.setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE);// 上下居中
            // format.setBorder(Border.ALL, BorderLineStyle.THIN,
            // Colour.BLACK);// 黑色边框
            // format.setBackground(Colour.YELLOW);// 黄色背景
        } catch (WriteException e) {
            e.printStackTrace();
        }
        return format;
    }
}