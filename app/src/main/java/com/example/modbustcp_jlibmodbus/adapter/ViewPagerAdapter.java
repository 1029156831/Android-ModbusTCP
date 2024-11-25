package com.example.modbustcp_jlibmodbus.adapter;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.example.modbustcp_jlibmodbus.fragment.FirstFragment;
import com.example.modbustcp_jlibmodbus.fragment.SecondFragment;
import com.example.modbustcp_jlibmodbus.fragment.ThirdFragment;

public class ViewPagerAdapter extends FragmentPagerAdapter {

    public ViewPagerAdapter(@NonNull FragmentManager fm) {
        super(fm);
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return new FirstFragment(); // 第一个页面
            case 1:
                return new SecondFragment(); // 第二个页面
            case 2:
                return new ThirdFragment(); // 第三个页面
            default:
                return null;
        }
    }

    @Override
    public int getCount() {
        return 3; // 页面数量
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return "数据表格";
            case 1:
                return "修改数据";
            case 2:
                return "仅读数据";
            default:
                return null;
        }
    }
}
