package com.google.ar.core.examples.java.common;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SimpleAdapter;

import com.google.ar.core.examples.java.Tree.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MyAdapter extends SimpleAdapter {
    private List<Map<String, Object>> mList;

    public MyAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
                     String[] from, int[] to) {
        super(context, data, resource, from, to);
        // 初始化 mList 成员变量
        mList = new ArrayList<>();
        mList.addAll((List<Map<String, Object>>) data);
//        this.mList = (List<Map<String, Object>>) data;
    }

    public void setmList(List<Map<String, Object>> mList) {
        this.mList.clear();
        this.mList.addAll(mList);
//        this.mList = mList;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the current item view
        View v = super.getView(position, convertView, parent);

        // Get the current checkbox and set its state
        CheckBox checkBox = v.findViewById(R.id.check_box);
        Boolean isChecked = (Boolean) mList.get(position).get("check");
        if(checkBox!=null)
            checkBox.setChecked(isChecked);
        return v;
    }

    // 添加自定义的 notifyDataSetChanged() 方法
    public void myNotifyDataSetChanged(List<Map<String, Object>> dataList) {
        if (dataList == null) {
            return;
        }
        mList.clear();
        mList.addAll(dataList);
        super.notifyDataSetChanged();
    }
}

