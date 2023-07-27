package com.google.ar.core.examples.java.common.helpers;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import java.util.ArrayList;

public class myPagerAdapter extends PagerAdapter{
    private ArrayList<View> viewlist;
    private ArrayList<String> titleList;

    public myPagerAdapter(ArrayList<View> viewList,ArrayList<String> titleList){
        viewlist = viewList;
        this.titleList=titleList;
    }

    /**
     * 获得viewpager中有多少个view
     * @return
     */
    @Override
    public int getCount() {
        return viewlist == null ? 0: viewlist.size();
    }

    /**
     * 将给定位置的view添加到ViewGroup(容器)中,创建并显示出来
     * @param container
     * @param position
     * @return
     */
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        container.addView(viewlist.get(position));
        return viewlist.get(position);
    }

    /**
     * 移除一个给定位置的页面。适配器有责任从容器中删除这个视图。 这是为了确保在finishUpdate(viewGroup)返回时视图能够被移除
     * @param container
     * @param position
     * @param object
     */
    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView(viewlist.get(position));
    }

    /**
     * 判断instantiateItem(ViewGroup, int position)函数所返回来的Key与一个页面视图是否是 代表的同一个View
     * @param view
     * @param object
     * @return
     */
    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return titleList.get(position);
    }
}
