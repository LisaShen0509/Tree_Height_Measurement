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

import java.io.File;
import java.io.IOException;

import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

import static com.google.ar.core.examples.java.common.helpers.ImageHelper.getBitmapByPath;


public class AbsDepthFragment extends BaseFragment {

    public static final String BUNDLE_TITLE = "title";

    private View mContentView;
    private Unbinder unbinder;
    private Long id;
    private String depthPath;
    private Bitmap DepthBitmap=null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContext = getActivity();
        mContentView = inflater.inflate(R.layout.fragment_absdepth_layout, container, false);
        try {
            initView();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mContentView;
    }

    private void initView() throws IOException {
        unbinder = ButterKnife.bind(this, mContentView);
        Bundle bundle = new Bundle();
        bundle = this.getArguments();
        id = bundle.getLong("id");
        DatabaseHelper TreeDBHelper = new DatabaseHelper(mContext, Constants.TREE_TABLE_NAME+".db",null,1);
        SQLiteDatabase TreeDB = TreeDBHelper.getReadableDatabase();
        Cursor treeCursor = TreeDB.rawQuery("select depth from "+Constants.TREE_TABLE_NAME+" where id="+id, null);

        while (treeCursor.moveToNext()){
            depthPath=treeCursor.getString(0);
        }
        File depth = new File(depthPath);
        if(depth.exists()){
            DepthBitmap=getBitmapByPath(depthPath);
        }
        ImageView image=mContentView.findViewById(R.id.depth);
        if(DepthBitmap!=null){
            image.setImageBitmap(DepthBitmap);
            image.setBackgroundResource(0);
        }
        else image.setBackgroundResource(R.drawable.noimage);
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

    public static AbsDepthFragment newInstance(String title) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_TITLE, title);
        AbsDepthFragment fragment = new AbsDepthFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

}