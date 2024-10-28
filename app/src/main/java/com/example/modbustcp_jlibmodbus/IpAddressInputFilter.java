package com.example.modbustcp_jlibmodbus;
import android.text.InputFilter;
import android.text.Spanned;


public class IpAddressInputFilter implements InputFilter {
    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        // 如果输入为空，允许删除
        if (source.length() == 0) {
            return null;
        }

        String input = dest.subSequence(0, dstart) + source.toString() + dest.subSequence(dend, dest.length());

        // 检查输入是否仅包含数字和小数点
        if (input.matches("[0-9.]*")) {
            return null; // 允许输入
        }
        
        return ""; // 禁止输入
    }


}
