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


public class AlignedDepthFragment extends BaseFragment {

    public static final String BUNDLE_TITLE = "title";

    private View mContentView;
    private Unbinder unbinder;
    private Long id;
    private String relDepthPath;
    private Bitmap RelDepthBitmap=null;
    private ImageView imageView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        System.out.println("调用一次onCreateView");
        mContext = getActivity();
        mContentView = inflater.inflate(R.layout.fragment_aligneddepth_layout, container, false);
        initView();
        return mContentView;
    }

    private void initView() {
        unbinder = ButterKnife.bind(this, mContentView);
        Bundle bundle = new Bundle();
        bundle = this.getArguments();
        id = bundle.getLong("id");
        DatabaseHelper TreeDBHelper = new DatabaseHelper(mContext, Constants.TREE_TABLE_NAME+".db",null,1);
        SQLiteDatabase TreeDB = TreeDBHelper.getReadableDatabase();
        Cursor treeCursor = TreeDB.rawQuery("select reldepth from "+Constants.TREE_TABLE_NAME+" where id="+id, null);

        while (treeCursor.moveToNext()){
            relDepthPath=treeCursor.getString(0);
        }
        File depth = new File(relDepthPath);
        if(depth.exists()){
            RelDepthBitmap = ImageHelper.getCompressedBitmap(1080,1440, depth);
        }
        imageView=mContentView.findViewById(R.id.reldepth);
        if(RelDepthBitmap!=null){
            imageView.setImageBitmap(RelDepthBitmap);
            imageView.setBackgroundResource(0);
        }
        else imageView.setBackgroundResource(R.drawable.noimage);
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
    }


    public void updateData(String newPath) {
        relDepthPath=newPath;
        System.out.println(relDepthPath);
    }
    public void updateUI() {
        File depth = new File(relDepthPath);
        if(depth.exists()){
            RelDepthBitmap = ImageHelper.getCompressedBitmap(1080,1440, depth);
        }
        if(RelDepthBitmap!=null){
            if(imageView==null){
                imageView=mContentView.findViewById(R.id.reldepth);
            }
            imageView.setImageBitmap(RelDepthBitmap);
            imageView.setBackgroundResource(0);
        }
        else imageView.setBackgroundResource(R.drawable.noimage);

    }

    public static AlignedDepthFragment newInstance(String title) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_TITLE, title);
        AlignedDepthFragment fragment = new AlignedDepthFragment();
        fragment.setArguments(bundle);
        return fragment;
    }


}