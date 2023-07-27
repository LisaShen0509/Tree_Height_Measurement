package com.google.ar.core.examples.java.common.fragment;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.ar.core.examples.java.Tree.R;
import com.google.ar.core.examples.java.common.helpers.Constants;
import com.google.ar.core.examples.java.common.helpers.DatabaseHelper;
import com.google.ar.core.examples.java.common.helpers.ImageHelper;

import java.io.File;

import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;


public class MaskFragment extends BaseFragment {

    public static final String BUNDLE_TITLE = "title";

    private static View mContentView;
    private Unbinder unbinder;
    private Long id;
    private String maskPath;
    private Bitmap MaskBitmap=null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContext = getActivity();
        mContentView = inflater.inflate(R.layout.fragment_mask_layout, container, false);
        initData();
        initView();
        ImageView image=mContentView.findViewById(R.id.mask);
        if(image==null){
            System.out.println("mask的imageView为空");
        }
        if(MaskBitmap!=null){
            image.setImageBitmap(MaskBitmap);
            image.setBackgroundResource(0);
        }
        else image.setBackgroundResource(R.drawable.noimage);
        return mContentView;
    }

    private void initView() {
        unbinder = ButterKnife.bind(this, mContentView);
    }

    //初始化数据
    private void initData() {
        Bundle bundle = new Bundle();
        bundle = this.getArguments();
        id = bundle.getLong("id");
        DatabaseHelper TreeDBHelper = new DatabaseHelper(mContext, Constants.TREE_TABLE_NAME+".db",null,1);
        SQLiteDatabase TreeDB = TreeDBHelper.getReadableDatabase();
        Cursor treeCursor = TreeDB.rawQuery("select mask from "+Constants.TREE_TABLE_NAME+" where id="+id, null);

        while (treeCursor.moveToNext()){
            maskPath=treeCursor.getString(0);
        }
        File image = new File(maskPath);
        if(image.exists()){
            MaskBitmap = ImageHelper.getCompressedBitmap(1080,1440, image);
        }
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
        updateUI(); // 更新UI界面
    }

    public void updateData(String newPath) {
        maskPath=newPath;
    }

    public void updateUI() {
        File image = new File(maskPath);
        if(image.exists()){
            MaskBitmap = ImageHelper.getCompressedBitmap(1080,1440, image);
        }
        if(mContentView==null)
            initView();
        ImageView imageView=mContentView.findViewById(R.id.mask);
        if(MaskBitmap!=null){
            imageView.setImageBitmap(MaskBitmap);
            imageView.setBackgroundResource(0);
        }
        else imageView.setBackgroundResource(R.drawable.noimage);
    }

    public static MaskFragment newInstance(String title) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_TITLE, title);
        MaskFragment fragment = new MaskFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

}