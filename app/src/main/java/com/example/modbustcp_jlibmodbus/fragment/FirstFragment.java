package com.example.modbustcp_jlibmodbus.fragment;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.InputFilter;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.example.modbustcp_jlibmodbus.utils.LogUtils;
import com.example.modbustcp_jlibmodbus.R;
import com.example.modbustcp_jlibmodbus.viewmodel.SharedViewModel;
import com.example.modbustcp_jlibmodbus.activity.Engineering_mode_Activity;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import info.hoang8f.widget.FButton;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class FirstFragment extends Fragment {
    private EditText searchInput;

    private ImageButton imageButtonSelect;
    private ImageButton imageButtonClean;

    // 来自cvtivity_engineering_mode.xml 的表格内容
    TableLayout tab_dynamics;

    //共享偏好实例
    //存储键值对形式的数据，SharedPreferences可以保存数据，下次打开软件可以读取数据
    //应用可以在不同的生命周期中持久化数据，确保用户的部分设置在应用关闭或重新启动后依然有效
    SharedPreferences preferences;

    //JSON全局配置
    //数据格式顺序（字节转换顺序），默认为CDAB，big-endian值为ABCD，little-endian值为DCBA，big-endian-swap为BADC，little-endian-swap为CDAB，
    static String data_format_order ="CDAB";

    //用于动态生成组件的容器
    LinearLayout container ;
    HorizontalScrollView hori_dynamics;
    //XML field页面控件定义
    //IP地址，端口，从站地址
    EditText input_IP, input_port,input_slave_address;
    //连接，断开，读取，停止读取，更改配置，恢复配置按钮
    Button button_connect,button_disconnect;

    //Modbus
    ModbusMaster modbusMaster;

    //从站地址
    private int slaveAddress=1;

    //连续读取失败次数
    int readFaliedCount = 0;

    // 定义一个标志变量来控制线程循环
    private volatile boolean isRunning = false;

    //主线程
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 创建一个HandlerThread
    HandlerThread handlerThread;
    Handler threadHandler;

    // 用于控制计时器的Handler
    final Handler timerHandler = new Handler();
    Runnable readRunnable;

    // 定义成员变量用于存储寄存器值
    private int[] inputRegisterValues;
    private int[] holdingRegisterValues;

    // 存储输入寄存器和保持寄存器的地址
    int minInputAddress = Integer.MAX_VALUE;
    int maxInputAddress = Integer.MIN_VALUE;
    int minHoldingAddress = Integer.MAX_VALUE;
    int maxHoldingAddress = Integer.MIN_VALUE;

    // 创建一个共享视图模型
    SharedViewModel sharedViewModel ;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup viewGroup, @Nullable Bundle savedInstanceState) {
        // 导入布局文件
        View view = inflater.inflate(R.layout.fragment_first, viewGroup, false);
        // 初始化视图组件
        imageButtonSelect = view.findViewById(R.id.imagebutton_select);
        imageButtonClean = view.findViewById(R.id.imagebutton_clean);
        searchInput = view.findViewById(R.id.search_input);
        container = view.findViewById(R.id.container);

        // 设置点击事件监听器
        final View.OnClickListener selectListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 清除所有编辑组件的焦点
                clearAllEditTextFocus();
                // 查询按钮
                if (v.getId() == R.id.imagebutton_select) {
                    String searchContent = searchInput.getText().toString();
                    searchItems(searchContent);
                }
                // 清除按钮
                else if (v.getId() == R.id.imagebutton_clean) {
                    String searchContent = "";
                    searchInput.setText(searchContent);
                    searchItems(searchContent);
                }
            }
        };

        imageButtonSelect.setOnClickListener(selectListener);
        imageButtonClean.setOnClickListener(selectListener);

        preferences =  getActivity().getSharedPreferences("Preference",0);
        // 修改 isLoad 的值，测试时使用
        //preferences.edit().putBoolean("isLoad_firstFragment", true).apply();
        // 检查是否是首次启动
        boolean isLoad = preferences.getBoolean("isLoad_firstFragment", true);

        //如果是首次启动，则加载数据，后续不再加载
        if (isLoad) {
            //第一次加载软件，需要初始化数据
            String jsonData = loadJSONFromAsset("config_Engineering_mode.json"); // 加载 JSON 文件
            loadJsonData(jsonData);
            preferences.edit().putBoolean("isLoad_firstFragment", false).apply();
        }
        else {
            // 如果不是首次启动，检查是否有存储的表格数据
            String savedData = preferences.getString("savedTableData_Engineering_mode", null);
            if(savedData != null) {
                loadJsonData(savedData); // 加载保存的数据
            }
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

//        ViewPager viewPager = getActivity().findViewById(R.id.viewPager);
//        // 设置 LayoutParams
//        ViewGroup.LayoutParams params = viewPager.getLayoutParams();
//        // 根据设置新高度
//        params.height = 6700;
//        viewPager.setLayoutParams(params); // 应用新的布局参数
//        viewPager.invalidate(); // 强制重绘
//        viewPager.requestLayout(); // 请求重新布局

        // 初始化viewModel
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        //注册观察者
        //订阅viewmodel中的数据变化
        sharedViewModel.getInput_slave_address().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String address) {
                // 在这里处理地址的变化
                slaveAddress = Integer.parseInt(address); // 将返回的值赋给 slaveAddress
            }
        });

        sharedViewModel.getIsRunning().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                // 在这里处理地址的变化
                isRunning = aBoolean; // 将返回的值赋给 slaveAddress
            }
        });

        sharedViewModel.getModbusMaster().observe(getViewLifecycleOwner(), new Observer<ModbusMaster>() {
            @Override
            public void onChanged(ModbusMaster modbusMaster_value) {
                // 在这里处理地址的变化
                modbusMaster = modbusMaster_value; // 将返回的值赋给 slaveAddress
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if(threadHandler!= null) {
            threadHandler.removeCallbacksAndMessages(null); // 移除所有未处理的消息
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (threadHandler != null) {
            threadHandler.removeCallbacksAndMessages(null); // 移除所有回调
        }
    }

    private void searchItems(String query) {
        // 遍历表格的每一行
        for (int i = 1; i < tab_dynamics.getChildCount(); i++) { // 从 1 开始，跳过表头
            TableRow tableRow = (TableRow) tab_dynamics.getChildAt(i);
            String modbusAddress = ((TextView) tableRow.getChildAt(0)).getText().toString();
            String bit = ((TextView) tableRow.getChildAt(1)).getText().toString();
            String functingName = ((TextView) tableRow.getChildAt(2)).getText().toString();

            // 根据查询条件判断行的可见性
            if (!query.isEmpty()) {
                if (modbusAddress.contains(query) || bit.contains(query) || functingName.contains(query)) {
                    tableRow.setVisibility(View.VISIBLE); // 显示匹配的行
                } else {
                    tableRow.setVisibility(View.GONE); // 隐藏不匹配的行
                }
            } else {
                tableRow.setVisibility(View.VISIBLE); // 显示所有行
            }
        }
    }

    // 清除所有编辑组件的焦点的方法
    private void clearAllEditTextFocus() {
        // 你可以实现焦点清除逻辑，比如遍历所有 EditText 并清除焦点
        if (getActivity() != null) {
            View currentFocus = getActivity().getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
            }
        }
    }

    int tabLayoutheigh = 0;

    // 读取 JSON 数据 显示组件内容
    private void loadJsonData(String jsonData) {
        // 记录方法开始执行的时间戳
        long startTime = System.currentTimeMillis();

        if (jsonData != null) {
            // 使用 RxJava 处理 JSON 数据的解析与显示
            Single.fromCallable(() -> {
                        // 读取JSON 全局配置
                        JSONObject jsonObject_globalConfig = new JSONObject(jsonData);
                        JSONObject globalConfig = jsonObject_globalConfig.getJSONObject("全局配置");
                        data_format_order = globalConfig.getString("数据格式顺序");

                        JSONObject jsonObject_data = new JSONObject(jsonData);
                        JSONArray dataArray = jsonObject_data.getJSONArray("数据");
                        return dataArray; // 返回解析后的 JSON 数组
                    })
                    .subscribeOn(Schedulers.io()) // 在 IO 线程上处理 JSON 解析
                    .observeOn(AndroidSchedulers.mainThread()) // 切换到主线程进行 UI 操作
                    .subscribe(dataArray -> {
                        // 创建一个水平滚动视图
                        hori_dynamics = new HorizontalScrollView(requireContext());
                        hori_dynamics.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,1.0f)); // 高度可以根据内容调整

                        // 创建 TableLayout 组件
                        tab_dynamics = new TableLayout(requireContext());
                        tab_dynamics.setLayoutParams(new TableLayout.LayoutParams(
                                TableLayout.LayoutParams.MATCH_PARENT,
                                TableLayout.LayoutParams.WRAP_CONTENT));
                        tab_dynamics.setStretchAllColumns(true);

                        // 添加表格标题
                        TableRow headerRow = new TableRow(requireContext());
                        headerRow.setLayoutParams(new TableRow.LayoutParams(
                                TableRow.LayoutParams.MATCH_PARENT,
                                TableRow.LayoutParams.WRAP_CONTENT));

                        // 创建表头
                        TableRow.LayoutParams headerLayoutParams = new TableRow.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                TableRow.LayoutParams.WRAP_CONTENT);
                        headerLayoutParams.weight = 1.0f;

                        TextView modbusHeader = createTextView("地址");
                        modbusHeader.setLayoutParams(headerLayoutParams);
                        //modbusHeader.setGravity(Gravity.LEFT);

                        TextView bitHeader = createTextView("位");
                        bitHeader.setLayoutParams(headerLayoutParams);
                        //bitHeader.setGravity(Gravity.LEFT);

                        TextView functionHeader = createTextView("功能");
                        functionHeader.setLayoutParams(headerLayoutParams);
//                        functionHeader.setLayoutParams(new TableRow.LayoutParams(
//                                TableRow.LayoutParams.FILL_PARENT,
//                                TableRow.LayoutParams.WRAP_CONTENT));

                        TextView readWriteHeader = createTextView("读写类型"); // 要隐藏的表头
                        readWriteHeader.setVisibility(View.GONE); // 隐藏该视图
                        readWriteHeader.setLayoutParams(new TableRow.LayoutParams(
                                0,
                                TableRow.LayoutParams.WRAP_CONTENT));

                        TextView unitHeader = createTextView("单位"); // 要隐藏的表头
                        unitHeader.setVisibility(View.GONE); // 隐藏该视图
                        unitHeader.setLayoutParams(new TableRow.LayoutParams(
                                0,
                                TableRow.LayoutParams.WRAP_CONTENT));

                        TextView valueHeader = createTextView("值");
                        valueHeader.setLayoutParams(headerLayoutParams);

                        TextView buttonHeader = createTextView("按钮"); // 要隐藏的表头
                        buttonHeader.setLayoutParams(headerLayoutParams);

                        TextView scalefactor = createTextView("缩放因子"); // 要隐藏的表头
                        scalefactor.setVisibility(View.GONE); // 隐藏该视图
                        scalefactor.setLayoutParams(new TableRow.LayoutParams(
                                0,
                                TableRow.LayoutParams.WRAP_CONTENT));

                        headerRow.addView(modbusHeader);
                        headerRow.addView(bitHeader);
                        headerRow.addView(functionHeader);
                        headerRow.addView(readWriteHeader); // 添加读写类型的表头
                        headerRow.addView(unitHeader); // 添加单位的表头
                        headerRow.addView(valueHeader);
                        headerRow.addView(buttonHeader);
                        headerRow.addView(scalefactor); // 添加缩放因子的表头
                        tab_dynamics.addView(headerRow);

                        // 设置高度为50dp
                        int heightInDp = 50;
                        float scale = requireContext().getResources().getDisplayMetrics().density;
                        int heightInPx = (int) (heightInDp * scale + 0.5f); // 将dp转换为px

                        tabLayoutheigh=0;
                        // 遍历 JSON 数据部分
                        for (int i = 0; i < dataArray.length(); i++) {
                            JSONObject jsonObject = dataArray.getJSONObject(i);
                            String modbusAddress = jsonObject.getString("MODBUS地址");
                            String bit = jsonObject.getString("位");
                            String functingName = jsonObject.getString("功能");
                            String readWrite = jsonObject.getString("读写类型");
                            String unit = jsonObject.getString("单位");
                            String value = jsonObject.getString("值");
                            String buttonType = jsonObject.getString("按钮类型");
                            String scaleFactor = jsonObject.getString("缩放因子");

                            //创建新的JSON对象，存储到内存中
                            if (!functingName.isEmpty()) {


                                TableRow tableRow = new TableRow(requireContext());
                                tableRow.setLayoutParams(new TableRow.LayoutParams(
                                        TableRow.LayoutParams.MATCH_PARENT,
                                        heightInPx,1.0f));
                                tableRow.setBackgroundResource(R.drawable.table_border); // 设置边框背景
                                tableRow.setMinimumHeight(heightInPx); // 设置最小高度
                                tableRow.setGravity(Gravity.CENTER_VERTICAL);

                                // MODBUS地址
                                TextView modbusAddressView = createTextView(modbusAddress);
                                modbusAddressView.setLayoutParams(getCenteredLayoutParams());
                                tableRow.addView(modbusAddressView);

                                //位
                                TextView bitView = createTextView("("+bit+")");
                                bitView.setLayoutParams(getCenteredLayoutParams());
                                tableRow.addView(bitView);

                                // 功能名称
                                TextView functingNameView = createTextView(functingName);
                                functingNameView.setLayoutParams(getCenteredLayoutParams());
                                tableRow.addView(functingNameView);

                                // 隐藏“读写类型”列
                                TextView readWriteView = createTextView(readWrite);
                                readWriteView.setVisibility(View.GONE); // 隐藏该视图
                                readWriteView.setLayoutParams(getHiddenLayoutParams());
                                tableRow.addView(readWriteView);

                                // 隐藏“单位”列
                                TextView unitView = createTextView(unit);
                                unitView.setVisibility(View.GONE); // 隐藏该视图
                                unitView.setLayoutParams(getHiddenLayoutParams());
                                tableRow.addView(unitView);

                                // 显示“值”列
                                TextView valueView = createTextView(value);
                                valueView.setLayoutParams(getCenteredLayoutParams());
                                tableRow.addView(valueView);

                                // 添加编辑按钮，仅在读写类型为 R/W 时添加
                                if (readWrite.equals("R/W") && !unit.equalsIgnoreCase("bit")) {
                                    addEditButton(tableRow, modbusAddress, bit, unit, value, buttonType,heightInPx);
                                } else if(readWrite.equals("R/W") && unit.equalsIgnoreCase("bit")) {
                                    if(unit.equalsIgnoreCase("bit")){
                                        // 创建开关样式的 ToggleButton
                                        ToggleButton toggleButton = new ToggleButton(requireContext());
                                        toggleButton.setTextOff("OFF"); // 关闭时的文本
                                        toggleButton.setTextOn("ON");  // 打开时的文本
                                        toggleButton.setChecked(false); // 初始化为关闭状态
                                        toggleButton.setTag(buttonType); // 设置按钮类型
                                        toggleButton.setEnabled(true); // 禁用
                                        // 设置背景色调
                                        ColorStateList colorStateList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray)); // 使用红色作为示例
                                        toggleButton.setBackgroundTintList(colorStateList);
                                        // 应用字体样式
                                        toggleButton.setTextAppearance(R.style.pingfang_bold);
                                        //设置固定高度
                                        toggleButton.setHeight(heightInPx);
                                        // 添加到表格行
                                        tableRow.addView(toggleButton);
                                        setToggleButtonTouchListener(modbusAddress, bit, toggleButton,buttonType);
                                    }
                                }else {
                                    if(unit.equalsIgnoreCase("bit")){
                                        // 创建开关样式的 ToggleButton
                                        ToggleButton toggleButton = new ToggleButton(requireContext());
                                        toggleButton.setTextOff("OFF"); // 关闭时的文本
                                        toggleButton.setTextOn("ON");  // 打开时的文本
                                        toggleButton.setChecked(false); // 初始化为关闭状态
                                        toggleButton.setTag(buttonType); // 设置按钮类型
                                        toggleButton.setEnabled(false); // 禁用

                                        // 设置背景色调
                                        ColorStateList colorStateList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray)); // 使用红色作为示例
                                        toggleButton.setBackgroundTintList(colorStateList);
                                        // 应用字体样式
                                        toggleButton.setTextAppearance(R.style.pingfang_bold);
                                        //设置固定高度
                                        toggleButton.setHeight(heightInPx);
                                        // 添加到表格行
                                        tableRow.addView(toggleButton);
                                    }
                                    else{
                                        ToggleButton toggleButton = new ToggleButton(requireContext());
                                        toggleButton.setVisibility(View.GONE);
                                        // 添加到表格行
                                        toggleButton.setHeight(heightInPx);
                                        // 添加到表格行
                                        tableRow.addView(toggleButton);
                                    }
                                }

                                // 隐藏“缩放因子”列
                                TextView saleFactorView = createTextView(scaleFactor);
                                saleFactorView.setVisibility(View.GONE); // 隐藏该视图
                                saleFactorView.setLayoutParams(getHiddenLayoutParams());
                                tableRow.addView(saleFactorView);
                                tab_dynamics.addView(tableRow); // 添加到表格中


                                //等待在当前视图及其父视图的布局和绘制工作完成之后执行
                                // 使用post方法获取实际高度
                                tableRow.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // 获取到tableRow的实际高度
                                        int actualHeight = tableRow.getHeight();

                                        //获取当前行的高度
                                        tabLayoutheigh+=actualHeight;
                                    }
                                });
                            }
                        }

                        // 更新 UI
                        if (container != null) {
                            container.removeAllViews();
                            //同样设置 tableLayout 的宽度占满
                            hori_dynamics.addView(tab_dynamics);
                            container.addView(hori_dynamics);
                        }

                        //等待在当前视图及其父视图的布局和绘制工作完成之后执行
                        container.post(new Runnable() {
                            @Override
                            public void run() {
                                // 获取 Activity 中的 ViewPager
                                ViewPager viewPager = requireActivity().findViewById(R.id.viewPager);
                                float scale_tabLayout = requireContext().getResources().getDisplayMetrics().density;
                                // 总行数的高度 + 头描述+搜索输入框+表头高度
                                int newHeightInPx = tabLayoutheigh + (int) ((40+50+30)*scale_tabLayout + 0.5f) ;//(int) ((tabLayoutheigh+40+30) * scale_tabLayout + 0.5f); // 转换为像素

                                //原始高度
                                int oldHeightInPx=viewPager.getHeight();
                                if( newHeightInPx>oldHeightInPx){
                                    // 设置 LayoutParams
                                    ViewGroup.LayoutParams params = viewPager.getLayoutParams();
                                    // 根据设置新高度
                                    params.height = newHeightInPx;
                                    viewPager.setLayoutParams(params); // 应用新的布局参数

                                    sharedViewModel.setFirstFragmentTabLayoutHeight(newHeightInPx);
                                }
                            }
                        });

                        // 保存数据到 SharedPreferences
                        preferences = getActivity().getSharedPreferences("Preference", 0);
                        preferences.edit().putString("savedTableData_Engineering_mode", jsonData).apply();

                        // 保存表格结构
                        saveTableStructure(dataArray);
                    }, throwable -> {
                        // 错误处理
                        Log.e("FirstFragmentError", "解析JSON失败: " + throwable.getMessage());
                    });
        } else {
            Log.e("FirstFragmentError", "加载JSON数据失败，jsonData为空");
        }
        // 记录方法执行完毕的时间戳
        long endTime = System.currentTimeMillis();
        // 计算并打印执行时间
        long duration = endTime - startTime;
        Log.i("JSON", "loadJsonData() 总执行时间：" + duration + "ms");
    }

    // 辅助方法来获取垂直居中的 LayoutParams
    private TableRow.LayoutParams getCenteredLayoutParams() {
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER_VERTICAL; // 设置垂直居中
        return params;
    }

    // 如果你有隐藏的视图，你可以通过这个方法来获取
    private TableRow.LayoutParams getHiddenLayoutParams() {
        return new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT);
    }

    private String loadJSONFromAsset(String fileName) {
        String json = null;
        try {
            InputStream is = getActivity().getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IllegalStateException illegalStateException){
            Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
        }catch (Exception e) {
            Log.e("FirstFragmentError", "loadJSONFromAsset失败，Exception:"+e.getMessage());
            Toast.makeText(requireContext(), "loadJSONFromAsset失败，Exception:", Toast.LENGTH_SHORT).show();
        }
        return json;
    }

    // 创建 TextView 的方法
    private TextView createTextView(String text) {
        TextView textView = new TextView(requireContext());
        textView.setMaxWidth(450);
        textView.setText(text);
        textView.setTextAppearance(R.style.pingfang_bold); // 应用样式
        return textView;
    }

    // 辅助方法：添加编辑按钮
    private void addEditButton(TableRow tableRow, String modbusAddress, String bit, String unit, String value, String buttonType,int heightInPx) {
        FButton editFButton = new FButton(requireContext());
        editFButton.setText("编辑");
        // 设置背景色调
        ColorStateList colorStateList_green = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green00bc12)); // 使用红色作为示例
        editFButton.setBackgroundTintList(colorStateList_green);
        editFButton.setTextAppearance(R.style.pingfang_bold);
        //设置固定高度
        editFButton.setHeight(heightInPx);

        editFButton.setOnClickListener((v) -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle("编辑值");

            // 创建一个布局用于输入
            LinearLayout layout = new LinearLayout(requireContext());
            layout.setOrientation(LinearLayout.VERTICAL);

            // 从tableRow获取当前值
            String currentValue = ((TextView) tableRow.getChildAt(5)).getText().toString();
            EditText valueInput = new EditText(requireContext());

            // 设置输入类型为数字或小数
            //valueInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL );
            valueInput.setText(currentValue); // 设置当前值
            valueInput.setFilters(new InputFilter[] { createNumberInputFilter() }); // 设置过滤器
            layout.addView(valueInput);
            builder.setView(layout);
            //按钮索引为6
            String scaleFactor_str = ((TextView) tableRow.getChildAt(7)).getText().toString(); //缩放因子在第八列，索引为7
            //缩放因子默认为1，如果为空则设置为1
            Integer scaleFactor_int = scaleFactor_str.isEmpty()?1:Integer.parseInt(scaleFactor_str);
            Float scaleFactor_float = scaleFactor_int.floatValue();

            builder.setPositiveButton("确认", (dialog, which) -> {
                String newValue = valueInput.getText().toString();
                ((TextView) tableRow.getChildAt(5)).setText(newValue); // 值在第6列

                // 使用 RxJava 处理数据发送
                handleValueWrite(modbusAddress,  slaveAddress, unit, newValue,scaleFactor_float);
            });

            builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
            builder.show();
        });
        tableRow.addView(editFButton);
    }


    // 辅助方法：处理值的写入
    private void handleValueWrite(String modbusAddress, Integer slaveAddress, String unit, String newValue,Float scaleFactor_float) {
        Single.fromCallable(() -> {
                    int address = Integer.parseInt(modbusAddress) - 40001;

                    if (unit.equals("Float")) {
                        float floatValue = scaleFactor_float==1.0f?Float.parseFloat(newValue):Float.parseFloat(newValue)*scaleFactor_float;
                        int[] floatToRegister = floatToRegisters(floatValue);
                        // 写入多个保持寄存器
                        modbusMaster.writeMultipleRegisters(slaveAddress, address, floatToRegister);
                    } else {
                        int intValue = scaleFactor_float==1.0f? Integer.parseInt(newValue):(int)(Float.parseFloat(newValue)*scaleFactor_float);
                        if(intValue<0){
                            intValue = 65536+intValue;
                        }

                        // 写入单个保持寄存器
                        modbusMaster.writeSingleRegister(slaveAddress, address, intValue);
                    }
                    return true; // 可以返回一个结果
                })
                .subscribeOn(Schedulers.io()) // 在 IO 线程上执行
                .observeOn(AndroidSchedulers.mainThread()) // 在主线程上观察结果
                .subscribe(result -> {
                    // 处理成功的结果，例如显示成功消息
                }, throwable -> {

                    // 处理错误
                    Log.e("FirstFragmentError", "ModbusTCP写入失败: " + throwable.getMessage());
                    if (modbusMasterCallback != null) {
                        switch (throwable.getClass().getSimpleName()){
                            case "ModbusProtocolException":
                                modbusMasterCallback.onWriteFailure_ModbusProtocolException(new Exception(throwable));
                                break;
                            case "ModbusNumberException":
                                modbusMasterCallback.onWriteFailure_ModbusNumberException(new Exception(throwable));
                                break;
                            case "ModbusIOException":
                                modbusMasterCallback.onWriteFailure_ModbusIOException(new Exception(throwable));
                                break;
                            case "NumberFormatException":
                                modbusMasterCallback.onWriteFailure_NumberFormatException(new Exception(throwable));
                                break;
                            case "IllegalArgumentException":
                                modbusMasterCallback.onWriteFailure_IllegalArgumentException(new Exception(throwable));
                                break;
                            case "TimeoutException":
                                modbusMasterCallback.onWriteFailure_TimeoutException(new Exception(throwable));
                                break;
                            default:
                                modbusMasterCallback.onWriteFailure_Exception(new Exception(throwable));
                                break;
                        }
                    }
                });
    }


    @SuppressLint("ClickableViewAccessibility")
    private void setToggleButtonTouchListener(String modbusAddress, String bit, ToggleButton toggleButton,String buttonType) {
        //设置两种组件，一种为点击式保持式，一种为长按式点动式
        //根据不同的组件去订阅不同的触发事件
        if(buttonType.contains("点动式")){
            // 添加长按效果
            toggleButton.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 长按时显示绿色
                        toggleButton.setText("ON");
                        // 设置背景色调
                        ColorStateList colorStateList_green = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green)); // 使用红色作为示例
                        toggleButton.setBackgroundTintList(colorStateList_green);

                        Thread down = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //发送数据到保持寄存器
                                // 判断newValue是否是float类型
                                try {
                                    int address=Integer.parseInt( modbusAddress)-40001;

                                    // 读取当前值
                                    int currentValue = modbusMaster.readHoldingRegisters(slaveAddress, address, 1)[0];

                                    //位操作
                                    int bitValue =  (currentValue  & (1 << Integer.parseInt(bit))) != 0 ?1:0; // 获取当前位
                                    if(bitValue != 1){
                                        bitValue = 1;
                                    }
                                    // 计算新的值，首先清除指定的位，然后设置为1
                                    int newValue = (currentValue & ~(1 << Integer.parseInt(bit))) | (bitValue << Integer.parseInt(bit));

                                    // 写入更改后的值
                                    modbusMaster.writeSingleRegister(slaveAddress, address, newValue);
                                } catch (NumberFormatException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                    }
                                } catch (ModbusProtocolException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }catch (IllegalStateException e){
                                    Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+e.getMessage());
                                }catch (Exception e){
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_Exception(e);
                                    }
                                }
                            }
                        });
                        down.start();
                        return   true;
                    case MotionEvent.ACTION_UP:

                    case MotionEvent.ACTION_CANCEL:
                        // 松开时变回灰色
                        toggleButton.setText("OFF");
                        // 设置背景色调
                        ColorStateList colorStateList_gray = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray)); // 使用红色作为示例
                        toggleButton.setBackgroundTintList(colorStateList_gray);

                        Thread cancel = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //发送数据到保持寄存器
                                // 判断newValue是否是float类型
                                try {
                                    int address=Integer.parseInt( modbusAddress)-40001;

                                    // 读取当前值
                                    int currentValue = modbusMaster.readHoldingRegisters(slaveAddress, address, 1)[0];

                                    // 设置当前位为0
                                    int bitPosition = Integer.parseInt(bit); // 当前位的位置
                                    int newValue = currentValue & ~(1 << bitPosition); // 清除当前位（设置为0）
                                    // 写入更改后的值
                                    modbusMaster.writeSingleRegister(slaveAddress, address, newValue);
                                } catch (NumberFormatException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                    }
                                } catch (ModbusProtocolException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }catch (IllegalStateException e){
                                    Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+e.getMessage());
                                }catch (Exception e){
                                    Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_Exception(e);
                                    }
                                }
                            }
                        });
                        cancel.start();
                        return   true;
                }
                return false;
            });
        }
        else if(buttonType.contains("保持式")){
            // 添加点击事件切换状态
            toggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // 当开关打开时
                    // 设置背景色调
                    ColorStateList colorStateList_green = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green)); // 使用红色作为示例
                    toggleButton.setBackgroundTintList(colorStateList_green);

                    Thread checked = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //发送数据到保持寄存器
                            // 判断newValue是否是float类型
                            try {
                                int address=Integer.parseInt( modbusAddress)-40001;

                                // 读取当前值
                                int currentValue = modbusMaster.readHoldingRegisters(slaveAddress, address, 1)[0];

                                //位操作
                                int bitValue =  (currentValue  & (1 << Integer.parseInt(bit))) != 0 ?1:0; // 获取当前位
                                if(bitValue != 1){
                                    bitValue = 1;
                                }
                                // 计算新的值，首先清除指定的位，然后设置为1
                                int newValue = (currentValue & ~(1 << Integer.parseInt(bit))) | (bitValue << Integer.parseInt(bit));

                                // 写入更改后的值
                                modbusMaster.writeSingleRegister(slaveAddress, address, newValue);
                            } catch (NumberFormatException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                }
                            } catch (ModbusProtocolException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                }
                            } catch (ModbusNumberException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                }
                            } catch (ModbusIOException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                }
                            }catch (IllegalStateException e){
                                Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+e.getMessage());
                            }catch (Exception e){
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_Exception(e);
                                }
                            }
                        }
                    });
                    checked.start();
                } else {
                    // 设置背景色调
                    ColorStateList colorStateList_gray = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray)); // 使用红色作为示例
                    toggleButton.setBackgroundTintList(colorStateList_gray);
                    Thread unchecked = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //发送数据到保持寄存器
                            // 判断newValue是否是float类型
                            try {
                                int address=Integer.parseInt( modbusAddress)-40001;

                                // 读取当前值
                                int currentValue = modbusMaster.readHoldingRegisters(slaveAddress, address, 1)[0];

                                // 设置当前位为0
                                int bitPosition = Integer.parseInt(bit); // 当前位的位置
                                int newValue = currentValue & ~(1 << bitPosition); // 清除当前位（设置为0）
                                // 写入更改后的值
                                modbusMaster.writeSingleRegister(slaveAddress, address, newValue);
                            } catch (NumberFormatException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                }
                            } catch (ModbusProtocolException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                }
                            } catch (ModbusNumberException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                }
                            } catch (ModbusIOException e) {
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                }
                            }catch (IllegalStateException illegalStateException){
                                Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                            }catch (Exception e){
                                Log.e("FirstFragmentError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_Exception(e);
                                }
                            }
                        }
                    });
                    unchecked.start();
                }
            });
        }
    }


    // 辅助方法：保存表格结构
    private void saveTableStructure(JSONArray jsonArray) {
        //保存表格结构
        JSONArray savedStructure = new JSONArray();
        try {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                JSONObject structObject = new JSONObject();
                structObject.put("MODBUS地址", jsonObject.optJSONObject("MODBUS地址"));
                structObject.put("位", jsonObject.optJSONObject("位"));
                structObject.put("功能", jsonObject.optJSONObject("功能"));
                structObject.put("读写类型", jsonObject.optJSONObject("读写类型"));
                structObject.put("单位", jsonObject.optJSONObject("单位"));
                structObject.put("值", jsonObject.optJSONObject("值"));
                savedStructure.put(structObject);
            }
        }catch (JSONException e) {
            Log.e("FirstFragmentError", "保存表格结构失败: " + e.getMessage());
        }

    }

    private InputFilter createNumberInputFilter() {
        return (source, start, end, dest, dstart, dend) -> {
            String input = source.toString();
            // 允许负号
            if (input.equals("-") && dstart == 0 && dest.length() == 0) {
                return null; // 允许负号在最前面
            }

            // 检查是否包含多于一个小数点
            if (input.equals(".") && dest.toString().contains(".")) {
                return ""; // 不允许多个小数点
            }

            // 使用正则表达式检查格式
            String newValue = dest.toString().substring(0, dstart) + input + dest.toString().substring(dend);
            if (!newValue.matches("-?\\d*\\.?\\d*")) {
                return ""; // 如果不匹配模式，则禁止输入
            }
            return null; // 允许输入
        };
    }

    //转换为小端处理
    private int[] floatToRegisters(float value) {
        if(data_format_order.equals("ABCD")){
            int intBits = Float.floatToIntBits(value); // 将 float 转换为位
            byte[] bytes = new byte[]{
                    (byte) ((intBits >>16) &0xFF),      // 高字节A
                    (byte) ((intBits >>24) &0xFF),      // 次高字节B
                    (byte) (intBits &0xFF),             // 次低字节C
                    (byte) ((intBits >>8) &0xFF)        // 低字节D
            };

            int count = bytes.length /2; // 每两个字节组成一个寄存器
            int[] registers = new int[count];

            for (int i =0; i < count; i++) {
                //组合高字节和低字节
                registers[i] = (bytes[i *2] &0xFF) | ((bytes[i *2 +1] &0xFF) <<8);
            }
            return registers;
        }
        else if(data_format_order.equals("DCBA")){
            int intBits = Float.floatToIntBits(value); // 将 float 转换为位
            byte[] bytes=new byte[]{
                    (byte) ((intBits >> 8) & 0xFF),     // 低字节D
                    (byte) (intBits & 0xFF),            // 次低字节C
                    (byte) ((intBits >> 24) & 0xFF),    // 次高字节B
                    (byte) ((intBits >> 16) & 0xFF)     // 高字节A
            };
            int count = bytes.length / 2; // 每两个字节组成一个寄存器
            int[] registers = new int[count];

            for (int i = 0; i < count; i++) {
                // 组合低字节和高字节
                registers[i] = (
                        bytes[i * 2] & 0xFF) | ((bytes[i * 2 + 1] & 0xFF) << 8);
            }
            // 创建一个字节数组来存储小端顺序的字节
            return  registers;
        }
        else if(data_format_order.equals("BADC")){
            int intBits = Float.floatToIntBits(value); // 将 float 转换为位
            byte[] bytes = new byte[]{
                    (byte) ((intBits >>24) &0xFF),  // 次高字节B
                    (byte) ((intBits >>16) &0xFF),  // 高字节A
                    (byte) ((intBits >>8) &0xFF),   // 低字节D
                    (byte) (intBits &0xFF)          // 次低字节C
            };

            int count = bytes.length /2; // 每两个字节组成一个寄存器
            int[] registers = new int[count];

            for (int i =0; i < count; i++) {
                //组合高字节和低字节
                registers[i] = (bytes[i *2] &0xFF) | ((bytes[i *2 +1] &0xFF) <<8);
            }
            return registers;
        }
        else //if(data_format_order.equals("CDAB"))
        {
            int intBits = Float.floatToIntBits(value); // 将 float 转换为位
            byte[] bytes=new byte[]{
                    (byte) (intBits & 0xFF),            // 次低字节C
                    (byte) ((intBits >> 8) & 0xFF),     // 低字节D
                    (byte) ((intBits >> 16) & 0xFF),    // 高字节A
                    (byte) ((intBits >> 24) & 0xFF)     // 次高字节B
            };

            int count = bytes.length / 2; // 每两个字节组成一个寄存器
            int[] registers = new int[count];

            for (int i = 0; i < count; i++) {
                // 组合低字节和高字节
                registers[i] = (
                        bytes[i * 2] & 0xFF) | ((bytes[i * 2 + 1] & 0xFF) << 8);
            }
            // 创建一个字节数组来存储小端顺序的字节
            return  registers;
        }
    }

    private Engineering_mode_Activity.ModbusMasterCallback modbusMasterCallback = new Engineering_mode_Activity.ModbusMasterCallback() {
        @Override
        public void onConnectSuccess(ModbusMaster master) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "连接成功" ,Toast.LENGTH_LONG).show();
                    //Toast.makeText(MainActivity.this, "ModbusTCP连接成功",Toast.LENGTH_SHORT).show();
                    button_connect.setEnabled(false);
                    ViewCompat.setBackgroundTintList(button_connect, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                    button_disconnect.setEnabled(true);
                    ViewCompat.setBackgroundTintList(button_disconnect, ColorStateList.valueOf(getResources().getColor(R.color.blue0075F6)));
                    sharedViewModel.setButtonStartReadEnabled(true);
                    sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));


                    //持久化保存IP、端口、从机地址
                    //持久化保存IP、端口、从机地址
                    preferences = getActivity().getSharedPreferences("Preference",0);
                    preferences.edit().putString("ipAddress",input_IP.getText().toString()).apply();
                    preferences.edit().putString("port",input_port.getText().toString()).apply();
                    preferences.edit().putString("slaveAddress",input_slave_address.getText().toString()).apply();
                }
            });
        }
        @Override
        public void onConnectModbusIOException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "连接失败，请检查网络及设备状态，详细信息：ModbusIOException:" + e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(getActivity(),"连接失败，请检查网络及设备状态，详细信息：ModbusIOException:"+e.getMessage());
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    sharedViewModel.setButtonStartReadEnabled(true);
                    sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));
                    sharedViewModel.setButtonStopReadEnabled(false);
                    sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));
                }
            });
        }
        @Override
        public void onConnectRuntimeException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "连接失败,RuntimeException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"连接失败,RuntimeException:"+e.getMessage());
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    sharedViewModel.setButtonStartReadEnabled(true);
                    sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));
                    sharedViewModel.setButtonStopReadEnabled(false);
                    sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));
                }
            });
        }
        @Override
        public void onConnectException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "连接失败，Exception:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"连接失败，Exception:"+e.getMessage());
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    sharedViewModel.setButtonStartReadEnabled(true);
                    sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));
                    sharedViewModel.setButtonStopReadEnabled(false);
                    sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));
                }
            });
        }

        @Override
        public void onReadSuccess(ModbusMaster master) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "读取成功",Toast.LENGTH_LONG).show();
                    readFaliedCount=0;
                }
            });
        }
        @Override
        public void onReadFailure_ModbusProtocolException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel中的数据
                        sharedViewModel.setIsRunning(isRunning);

                        sharedViewModel.setButtonStartReadEnabled(false);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.gray));
                        sharedViewModel.setButtonStopReadEnabled(false);
                        sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));

                        sharedViewModel.setButtonConnectEnabled(true);
                        sharedViewModel.setButtonConnectBackgroundColor(getResources().getColor(R.color.blue0075F6));
                        sharedViewModel.setButtonDisconnectEnabled(false);
                        sharedViewModel.setButtonDisconnectBackgroundColor(getResources().getColor(R.color.gray));
                    }
                }
            });
        }
        @Override
        public void onReadFailure_ModbusNumberException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "读取失败，数值异常，异常信息："+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"读取失败，数值异常，异常信息："+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel中的数据
                        sharedViewModel.setIsRunning(isRunning);

                        sharedViewModel.setButtonStartReadEnabled(false);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.gray));
                        sharedViewModel.setButtonStopReadEnabled(false);
                        sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));

                        sharedViewModel.setButtonConnectEnabled(true);
                        sharedViewModel.setButtonConnectBackgroundColor(getResources().getColor(R.color.blue0075F6));
                        sharedViewModel.setButtonDisconnectEnabled(false);
                        sharedViewModel.setButtonDisconnectBackgroundColor(getResources().getColor(R.color.gray));
                    }
                }
            });
        }
        @Override
        public void onReadFailure_ModbusIOException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "读取失败，读取超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"读取失败，读取超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel中的数据
                        sharedViewModel.setIsRunning(isRunning);

                        sharedViewModel.setButtonStartReadEnabled(false);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.gray));
                        sharedViewModel.setButtonStopReadEnabled(false);
                        sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));

                        sharedViewModel.setButtonConnectEnabled(true);
                        sharedViewModel.setButtonConnectBackgroundColor(getResources().getColor(R.color.blue0075F6));
                        sharedViewModel.setButtonDisconnectEnabled(false);
                        sharedViewModel.setButtonDisconnectBackgroundColor(getResources().getColor(R.color.gray));
                    }
                }
            });
        }
        @Override
        public void onReadFailure_Exception(Exception e) {
            try {
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(requireContext(), "读取失败，详细信息：Exception:"+e.getMessage(),Toast.LENGTH_LONG).show();
                        LogUtils.logError(requireContext(),"读取失败，详细信息：Exception:"+e.getMessage());

                        readFaliedCount++;
                        if(readFaliedCount>=3){
                            isRunning=false;
                            //更新viewmodel中的数据
                            sharedViewModel.setIsRunning(isRunning);

                            sharedViewModel.setButtonStartReadEnabled(false);
                            sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.gray));
                            sharedViewModel.setButtonStopReadEnabled(false);
                            sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));

                            sharedViewModel.setButtonConnectEnabled(true);
                            sharedViewModel.setButtonConnectBackgroundColor(getResources().getColor(R.color.blue0075F6));
                            sharedViewModel.setButtonDisconnectEnabled(false);
                            sharedViewModel.setButtonDisconnectBackgroundColor(getResources().getColor(R.color.gray));
                        }
                    }
                });
            }catch (IllegalStateException illegalStateException){
                Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                isRunning=false;
            }
        }

        @Override
        public void onWriteFailure_ModbusProtocolException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，协议异常，详细信息："+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"写入失败，协议异常，详细信息："+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_ModbusNumberException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"写入失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_ModbusIOException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，写入超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"写入失败，写入超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_NumberFormatException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，数字格式异常，详细信息：NumberFormatException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"写入失败，数字格式异常，详细信息：NumberFormatException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_IllegalArgumentException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，参数无效，详细信息：IllegalArgumentException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"写入失败，参数无效，详细信息：IllegalArgumentException:"+e.getMessage());
                }
            });
        }
        @Override
        public Void onWriteFailure_TimeoutException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，不符合预期的操作，详细信息：RuntimeException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"写入失败，不符合预期的操作，详细信息：RuntimeException:"+e.getMessage());
                }
            });
            return null;
        }
        @Override
        public void onWriteFailure_Exception(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，详细信息：Exception:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(requireContext(),"写入失败，详细信息：Exception:"+e.getMessage());
                }
            });
        }
    };


    public void StartRead() {
        //销毁之前的线程
        if (handlerThread != null && handlerThread.isAlive()) {
            handlerThread.quitSafely(); // 停止线程
            handlerThread = null;
        }
        if(threadHandler!=null){
            threadHandler.removeCallbacksAndMessages(null);
            threadHandler=null;
        }

        handlerThread = new HandlerThread("ModbusReaderThread");
        handlerThread.start(); // 启动线程
        // 创建一个Handler与HandlerThread关联
        threadHandler= new Handler(handlerThread.getLooper());;

        // 定义一个Runnable，用于重复执行读取操作
        readRunnable = new Runnable(){
            @Override
            public void run() {
                if (!isRunning) {
                    return; // 如果标志为false，直接返回
                }
                // 第一次遍历表格以查找地址
                int tabCount = tab_dynamics.getChildCount();
                for (int i = 1; i < tab_dynamics.getChildCount(); i++) {
                    TableRow tableRow = (TableRow) tab_dynamics.getChildAt(i);
                    TextView modbusAddressView = (TextView) tableRow.getChildAt(0);
                    int modbusAddress = Integer.parseInt(modbusAddressView.getText().toString());

                    // 判断是输入寄存器还是保持寄存器
                    if (String.valueOf(modbusAddress).startsWith("4")) {
                        // 更新保持寄存器的最小和最大地址
                        minHoldingAddress = Math.min(minHoldingAddress, modbusAddress);
                        maxHoldingAddress = Math.max(maxHoldingAddress, modbusAddress);
                    } else if (String.valueOf(modbusAddress).startsWith("3")) {
                        // 更新输入寄存器的最小和最大地址
                        minInputAddress = Math.min(minInputAddress, modbusAddress);
                        maxInputAddress = Math.max(maxInputAddress, modbusAddress);
                    }
                }

                try {
                    // 只执行一次输入寄存器的读取
                    if (minInputAddress <= maxInputAddress) {
                        inputRegisterValues = modbusMaster.readInputRegisters(slaveAddress, minInputAddress - 30001, maxInputAddress - minInputAddress + 1);
                    }

                    // 只执行一次保持寄存器的读取
                    if (minHoldingAddress <= maxHoldingAddress) {
                        holdingRegisterValues = modbusMaster.readHoldingRegisters(slaveAddress, minHoldingAddress - 40001, maxHoldingAddress - minHoldingAddress + 1);
                    }

                    // 第一次遍历表格以查找地址
                    for (int i = 1; i < tab_dynamics.getChildCount(); i++) {
                        TableRow tableRow = (TableRow) tab_dynamics.getChildAt(i);
                        // 找到 MODBUS 地址和 值 的 TextView
                        TextView modbusAddressView = (TextView) tableRow.getChildAt(0); // MODBUS地址在第一列，索引为0
                        TextView bitView = (TextView) tableRow.getChildAt(1); // 位在第一列，索引为1
                        TextView functionNameView = (TextView) tableRow.getChildAt(2); // 功能名称在第二列，索引为2
                        //功能名称索引为2
                        TextView readWriteTypeView = (TextView) tableRow.getChildAt(3); // 读写类型在第四列，索引为3
                        TextView uintView = (TextView) tableRow.getChildAt(4); // 单位在第五列，索引为4
                        TextView valueView = (TextView) tableRow.getChildAt(5); // 值在第六列，索引为5
                        //按钮索引为6
                        TextView scaleFactorView = (TextView) tableRow.getChildAt(7); //缩放因子在第八列，索引为7

                        int modbusAddress = Integer.parseInt(modbusAddressView.getText().toString()); // 将 MODBUS 地址转换为整数，绝对地址
                        //去掉括号
                        String bit = bitView.getText().toString().replace("(", "").replace(")", "").trim();
                        String functionName = functionNameView.getText().toString();
                        String readWriteType = readWriteTypeView.getText().toString();
                        String uint = uintView.getText().toString();
                        //缩放因子默认为1，如果为空则设置为1
                        Integer scaleFactor_int = scaleFactorView.getText().toString().isEmpty()?1:Integer.parseInt(scaleFactorView.getText().toString());
                        Float scaleFactor_float = scaleFactor_int.floatValue();
                        // 确定寄存器的数量，Float类型由两个 16 位的保持寄存器组合在一起形成
                        int quantity = uint.equals("Float") ? 2 : 1;
                        int[] registerValues = null;
                        if("R/W".equals(readWriteType))
                        {
                            int startIndex = modbusAddress - minHoldingAddress;
                            int endIndex = startIndex + quantity;
                            // 确保 startIndex 和 endIndex 在有效范围内
                            if (holdingRegisterValues != null && startIndex >= 0 && endIndex <= holdingRegisterValues.length && startIndex < endIndex) {
                                registerValues = Arrays.copyOfRange(holdingRegisterValues, startIndex, endIndex);
                            }
                        }else {
                            int startIndex = modbusAddress - minInputAddress;
                            int endIndex = startIndex + quantity;
                            // 确保 startIndex 和 endIndex 在有效范围内
                            if (inputRegisterValues != null && startIndex >= 0 && endIndex <= inputRegisterValues.length && startIndex < endIndex) {
                                registerValues = Arrays.copyOfRange(inputRegisterValues, startIndex, endIndex);
                            }
                        }
                        if (registerValues != null && registerValues.length > 0) {
                            //位
                            switch (uint) {
                                case "bit":
                                    ToggleButton toggleButton = (ToggleButton) tableRow.getChildAt(6);// 按钮在第七列，索引为6，也可能没有按钮
                                    // 将读取到的寄存器值进行位操作后显示在TextView中
                                    for (int j = 0; j < quantity; j++) {
                                        //位操作
                                        int bitValue = (registerValues[j] & (1 << Integer.parseInt(bit))) != 0 ? 1 : 0; // 获取当前位

                                        // 在UI线程中更新TextView
                                        mainHandler.post(() ->{
                                            try {
                                                valueView.setText(String.valueOf(bitValue));
                                                // 根据bitValue设置背景颜色
                                                if (bitValue == 1) {
                                                    toggleButton.setText("ON");
                                                    // 设置背景色调
                                                    ColorStateList colorStateList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green));
                                                    toggleButton.setBackgroundTintList(colorStateList);
                                                } else {
                                                    toggleButton.setText("OFF");
                                                    // 设置背景色调
                                                    ColorStateList colorStateList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray));
                                                    toggleButton.setBackgroundTintList(colorStateList);
                                                }
                                            }catch (IllegalStateException e){
                                                Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+e.getMessage());
                                                isRunning=false;
                                            }
                                        });
                                    }
                                    break;
                                case "UINT16":
                                    for (int j = 0; j < quantity; j++) {
                                        String stringValue = scaleFactor_float==1.0f?String.valueOf(registerValues[j]):String.valueOf(registerValues[j]/scaleFactor_float);
                                        // 将读取到的寄存器值直接显示在TextView中
                                        mainHandler.post(() ->{
                                            //根据名称来将数值转换为字符串
                                            if(functionName.equals("惯导状态"))
                                            {
                                                switch (stringValue){
                                                    case "0":
                                                        valueView.setText("准备");
                                                        break;
                                                    case "2":
                                                        valueView.setText("对准状态");
                                                        break;
                                                    case "4":
                                                        valueView.setText("对准完成，进入导航状态");
                                                        break;
                                                    case "8":
                                                        valueView.setText("快速的启动失败");
                                                        break;
                                                    case "10":
                                                        valueView.setText("惯导内部故障");
                                                        break;
                                                    default:
                                                        valueView.setText(stringValue);
                                                        break;
                                                }
                                            }
                                            else{
                                                valueView.setText(stringValue);
                                            }
                                        });
                                    }
                                    break;
                                case "INT16":
                                    // 将读取到的寄存器值，直接显示在TextView中
                                    for (int j = 0; j < quantity; j++) {
                                        // 将16位无符号整数转换为有符号整数
                                        int value = registerValues[j];
                                        if (value > 32767) { // 处理负数
                                            value -= 65536; // 如果是负数，调整到正确的范围
                                        }
                                        String stringValue = scaleFactor_float==1.0f?String.valueOf(value):String.valueOf(value/scaleFactor_float);
                                        // 将读取到的寄存器值直接显示在TextView中
                                        mainHandler.post(() -> valueView.setText(stringValue));
                                    }
                                    break;
                                case "Float":
                                    for (int j = 0; j < quantity; j += 2) {
                                        int low = registerValues[j];//低字节
                                        int high = registerValues[j + 1];//高字节

                                        Float valueViewFloat = registersToFloat(low,high);

                                        // 将读取到的寄存器值直接显示在TextView中
                                        mainHandler.post(() -> valueView.setText(String.valueOf(valueViewFloat)));
                                    }
                                    break;
                            }
                        }

                    }
//                    // 读取成功后调用回调，注释
//                    if (modbusMasterCallback!= null) {
//                        modbusMasterCallback.onReadSuccess(modbusMaster);
//                    }
                }
                catch (ModbusProtocolException e) {
                    Log.e("FirstFragmentError", "读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage());
                    // 读取失败后调用回调
                    if (modbusMasterCallback!= null) {
                        modbusMasterCallback.onReadFailure_ModbusProtocolException(e);
                    }
                } catch (ModbusNumberException e) {
                    Log.e("FirstFragmentError", "读取失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_ModbusNumberException(e);
                    }
                } catch (ModbusIOException e) {
                    Log.e("FirstFragmentError", "读取失败，读取超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_ModbusIOException(e);
                    }
                }catch (IllegalStateException illegalStateException){
                    Log.e("FirstFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                    isRunning=false;
                }catch (Exception e){
                    Log.e("FirstFragmentError", "读取失败，详细信息：Exception:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_Exception(e);
                    }
                }
                finally {
                    // 始终调度下一个读取
                    if (isRunning) {
                        if(threadHandler!= null){
                            threadHandler.postDelayed(this, 1000); // 每隔1000ms（1秒）再次执行
                        }
                    } else {
                        if(handlerThread != null) {
                            handlerThread.quitSafely(); // 安全退出线程
                        }
                    }
                }
            }

            //格局数据格式顺序来进行数据格式转换
            private Float  registersToFloat (int low,int high) {
                if(data_format_order.equals("ABCD")) {
                    //ABCD
                    // 组合寄存器中的两个16位整数
                    byte[] bytes3 = new byte[]{
                            (byte) (high & 0xFF),          // 高字节A
                            (byte) ((high >> 8) & 0xFF),   // 次高字节B
                            (byte) (low & 0xFF),          // 次低字节C
                            (byte) ((low >> 8) & 0xFF)   // 低字节D
                    };
                    // 将字节转换为整型
                    int intBits3 = (bytes3[0] & 0xFF)
                            | ((bytes3[1] & 0xFF) << 8)
                            | ((bytes3[2] & 0xFF) << 16)
                            | ((bytes3[3] & 0xFF) << 24);
                    // 转换为浮点数
                    return Float.intBitsToFloat(intBits3);
                }
                else if(data_format_order.equals("DCBA")) {
                    //DCBA
                    // 组合寄存器中的两个16位整数
                    byte[] bytes4 = new byte[]{
                            (byte) ((low >> 8) & 0xFF),      // 低字节D
                            (byte) (low & 0xFF),            // 次低字节C
                            (byte) ((high >> 8) & 0xFF),     // 次高字节B
                            (byte) (high & 0xFF)            // 高字节A
                    };
                    int intBits4 = (bytes4[0] & 0xFF)
                            | ((bytes4[1] & 0xFF) << 8)
                            | ((bytes4[2] & 0xFF) << 16)
                            | ((bytes4[3] & 0xFF) << 24);
                    return Float.intBitsToFloat(intBits4);
                }
                else if(data_format_order.equals("BADC")) {
                    //BADC
                    // 组合寄存器中的两个16位整数
                    byte[] bytes5 = new byte[]{
                            (byte) ((high >> 8) & 0xFF),   // 次高字节B
                            (byte) (high & 0xFF),          // 高字节A
                            (byte) ((low >> 8) & 0xFF),   // 低字节D
                            (byte) (low & 0xFF)          // 次低字节C
                    };
                    // 将字节转换为整型
                    int intBits5 = (bytes5[0] & 0xFF)
                            | ((bytes5[1] & 0xFF) << 8)
                            | ((bytes5[2] & 0xFF) << 16)
                            | ((bytes5[3] & 0xFF) << 24);
                    // 转换为浮点数
                    return Float.intBitsToFloat(intBits5);
                }
                else // if(data_format_order.equals("CDAB")) //也是默认
                {
                    //CDAB
                    // 组合寄存器中的两个16位整数
                    byte[] bytes6 = new byte[]{
                            (byte) (low & 0xFF),            // 次低字节C
                            (byte) ((low >> 8) & 0xFF),      // 低字节D
                            (byte) (high & 0xFF),            // 高字节A
                            (byte) ((high >> 8) & 0xFF)     // 次高字节B
                    };
                    int intBits6 = (bytes6[0] & 0xFF)
                            | ((bytes6[1] & 0xFF) << 8)
                            | ((bytes6[2] & 0xFF) << 16)
                            | ((bytes6[3] & 0xFF) << 24);
                    return Float.intBitsToFloat(intBits6);
                }
            }
        };

        // 添加Runnable到Handler处理
        threadHandler.post(readRunnable);
    }

    public void StopRead() {
        //关闭线程
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null; // 清空引用
        }
        // 移除Runnable
        if (threadHandler != null) {
            threadHandler.removeCallbacks(readRunnable);
            threadHandler = null; // 清空引用
        }
    }
}
