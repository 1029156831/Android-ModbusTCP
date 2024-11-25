package com.example.modbustcp_jlibmodbus.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.viewpager.widget.ViewPager;

import com.example.modbustcp_jlibmodbus.fragment.FirstFragment;
import com.example.modbustcp_jlibmodbus.fragment.SecondFragment;
import com.example.modbustcp_jlibmodbus.fragment.ThirdFragment;
import com.example.modbustcp_jlibmodbus.utils.IpAddressInputFilter;
import com.example.modbustcp_jlibmodbus.utils.LogUtils;
import com.example.modbustcp_jlibmodbus.R;
import com.example.modbustcp_jlibmodbus.utils.ModbusUtils;
import com.example.modbustcp_jlibmodbus.viewmodel.SharedViewModel;
import com.example.modbustcp_jlibmodbus.adapter.ViewPagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import info.hoang8f.widget.FButton;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.List; // 导入 List 接口
import androidx.lifecycle.ViewModelProvider;


/** @noinspection deprecation*/
public class Engineering_mode_Activity extends AppCompatActivity {

    //XML field页面控件定义
    //IP地址，端口，从站地址
    EditText input_IP, input_port,input_slave_address;
    //连接，断开，读取，停止读取，更改配置，恢复配置按钮
    Button button_connect,button_disconnect,button_start_readMB,button_stop_readMB;
    //用于动态生成组件的容器
    LinearLayout container ;
    HorizontalScrollView hori_dynamics;
    // 创建动态的 TableLayout 组件
    TableLayout tab_dynamics;
    //工具栏
    Toolbar toolbar;
//    //搜索按钮
//    ImageButton imageButtonSelect,imageButtonClean;
    //搜索内容
//    EditText search_input;
    //导航栏
    TabLayout tabLayout;
    ViewPager viewPager;
    //XML field

    //主线程
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 定义一个标志变量来控制线程循环
    private volatile boolean isRunning = false;
    // 创建一个HandlerThread
    HandlerThread handlerThread;
    Handler threadHandler;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 用于控制计时器的Handler
    final Handler timerHandler = new Handler();
    Runnable readRunnable;

    //Modbus
    ModbusMaster modbusMaster;
    TcpParameters tcpParameters;
    //IP地址
    private String ipAddress="192.168.1.10";
    //端口
    private int port=503;
    //从站地址
    private int slaveAddress=1;

    //共享偏好实例
    //存储键值对形式的数据，SharedPreferences可以保存数据，下次打开软件可以读取数据
    //应用可以在不同的生命周期中持久化数据，确保用户的部分设置在应用关闭或重新启动后依然有效
    SharedPreferences preferences;
    //颜色
    private int green;
    private int gray;

    //管理网络连接类
    ConnectivityManager conMgr;

    // 定义成员变量用于存储寄存器值
    private int[] inputRegisterValues;
    private int[] holdingRegisterValues;

    // 存储输入寄存器和保持寄存器的地址
    int minInputAddress = Integer.MAX_VALUE;
    int maxInputAddress = Integer.MIN_VALUE;
    int minHoldingAddress = Integer.MAX_VALUE;
    int maxHoldingAddress = Integer.MIN_VALUE;

    //连续读取失败次数
    int readFaliedCount = 0;


    //JSON全局配置
    //数据格式顺序（字节转换顺序），默认为CDAB，big-endian值为ABCD，little-endian值为DCBA，big-endian-swap为BADC，little-endian-swap为CDAB，
    static String data_format_order ="CDAB";

    //共享的ViewModel
    SharedViewModel sharedViewModel;
    //tabLayout高度
    int firstFragmentTabLayoutHeight,secondFragmentTabLayoutHeight,thirdFragmentTabLayoutHeight = 0;

    ModbusUtils modbusUtils = new ModbusUtils();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_engineering_mode);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 在 onCreate() 方法中使用 ViewModelProvider
        sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        sharedViewModel.getIsRunning().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                // 在这里处理地址的变化
                isRunning = aBoolean; // 将返回的值赋给 slaveAddress
            }
        });

        //find view and get the listeners设置组件监听器
        initial();

        preferences = getSharedPreferences("Preference",0);
        // 修改 isLoad 的值，测试时使用
        //preferences.edit().putBoolean("isLoad_engineering_mode", true).apply();
        // 检查是否是首次启动
        boolean isLoad = preferences.getBoolean("isLoad_engineering_mode", true);

        //如果是首次启动，则加载数据，后续不再加载
        if (isLoad) {
            //第一次加载软件，需要初始化数据
            String jsonData = loadJSONFromAsset("config_Engineering_mode.json"); // 加载 JSON 文件
            loadJsonData(jsonData);
            preferences.edit().putBoolean("isLoad_engineering_mode", false).apply();
        }
        else {
            // 如果不是首次启动，检查是否有存储的表格数据
            String savedData = preferences.getString("savedTableData_Engineering_mode", null);
            if(savedData != null) {
                loadJsonData(savedData); // 加载保存的数据
            }
        }

        conMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        //读取用户之前保存的IP地址。如果该地址未被存储，则返回空字符串
        String pref_IP = preferences.getString("ipAddress","");
        //如果IP地址为空，则使用默认IP地址
        if(!pref_IP.isEmpty()){
            ipAddress = pref_IP;
            input_IP.setText(pref_IP);
        }
        //读取用户之前保存的Port。如果该未被存储，则返回空字符串
        String pref_port = preferences.getString("port_Engineering_mode","");
        if(!pref_port.isEmpty()){
            port = Integer.parseInt(pref_port);
            input_port.setText(pref_port);
        }
        String pref_slaveaddress = preferences.getString("slaveAddress","");
        if(!pref_slaveaddress.isEmpty()){
            slaveAddress = Integer.parseInt(pref_slaveaddress);
            input_slave_address.setText(pref_slaveaddress);
            sharedViewModel.setInput_slave_address(String.valueOf(slaveAddress));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_more, menu);

        // 获取菜单项
        MenuItem moreItem = menu.findItem(R.id.action_more_option1);
        // 遍历菜单项，设置样式
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            SpannableString style = new SpannableString(item.getTitle());
            style.setSpan(new TextAppearanceSpan(this, R.style.puhuiti_bold), 0, style.length(), 0);
            item.setTitle(style);
        }

        if (moreItem != null) {
            Drawable icon = moreItem.getIcon();
            if (icon != null) {
                // 设置图标颜色
                icon.setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_IN); // 设定菜单图标颜色为白色
                moreItem.setIcon(icon);
            }
        }

        // 您可以对其他菜单项做类似处理
        MenuItem moreItem2 = menu.findItem(R.id.action_more_option2);
        if (moreItem2 != null) {
            Drawable icon2 = moreItem2.getIcon();
            if (icon2 != null) {
                icon2.setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_IN);
                moreItem2.setIcon(icon2);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_more_option1){
            // 处理选项1的点击事件
            checkAndRequestPermissions();
            return true;
        }
        else if(item.getItemId() == R.id.action_more_option2){
            // 处理选项2的点击事件
            //恢复assets内json文件
            String jsonData = loadJSONFromAsset("config_Engineering_mode.json"); // 加载 JSON 文件
            loadJsonData(jsonData);
            return true;
        }
        else if(item.getItemId() == R.id.action_more_option3){
            //弹出日志窗口
            showLogDialog();

            return true;
        }
        else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isConnected", modbusMaster != null && modbusMaster.isConnected());
        outState.putInt("port", port);
        outState.putString("ipAddress", ipAddress);
        outState.putInt("slaveAddress", slaveAddress);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        boolean isConnected = savedInstanceState.getBoolean("isConnected", false);
        port = savedInstanceState.getInt("port", 503);
        ipAddress = savedInstanceState.getString("ipAddress", "192.168.1.10");
        slaveAddress = savedInstanceState.getInt("slaveAddress", 1);
    }

    private void initial() {
        input_IP = findViewById(R.id.input_IP);
        input_IP.setFilters(new InputFilter[] { new IpAddressInputFilter() });
        input_IP.post(new Runnable() {
            @Override
            public void run() {
                input_IP.setFocusable(true);
                input_IP.setFocusableInTouchMode(true);
            }
        });

        input_port = findViewById(R.id.input_port);
        input_port.clearFocus();

        input_slave_address = findViewById(R.id.input_slave_address);
        input_slave_address.clearFocus();

        button_connect = findViewById(R.id.button_connect);
        sharedViewModel.getButtonConnectEnabled().observe(this,isEnable -> {
            button_connect.setEnabled(isEnable);
        });
        sharedViewModel.getButtonConnectBackgroundColor().observe(this, color -> {
            ViewCompat.setBackgroundTintList(button_connect, ColorStateList.valueOf(color));
        });
        button_disconnect = findViewById(R.id.button_disconnect);
        sharedViewModel.getButtonDisconnectEnabled().observe(this,isEnable -> {
            button_disconnect.setEnabled(isEnable);
        });
        sharedViewModel.getButtonDisconnectBackgroundColor().observe(this, color -> {
            ViewCompat.setBackgroundTintList(button_disconnect, ColorStateList.valueOf(color));
        });
        button_start_readMB = findViewById(R.id.button_start_readMB);
        sharedViewModel.getButtonStartReadEnabled().observe(this, isEnabled -> {
            button_start_readMB.setEnabled(isEnabled);
        });
        sharedViewModel.getButtonStartReadBackgroundColor().observe(this, color -> {
            ViewCompat.setBackgroundTintList(button_start_readMB, ColorStateList.valueOf(color));
        });
        button_stop_readMB = findViewById(R.id.button_stop_readMB);
        sharedViewModel.getButtonStopReadEnabled().observe(this, isEnabled -> {
            button_stop_readMB.setEnabled(isEnabled);
        });
        sharedViewModel.getButtonStopReadBackgroundColor().observe(this, color -> {
            ViewCompat.setBackgroundTintList(button_stop_readMB, ColorStateList.valueOf(color));
        });

        container = findViewById(R.id.container);

        // 初始化背景颜色
        green = ContextCompat.getColor(this, R.color.green);
        gray = ContextCompat.getColor(this, R.color.ltgray);

        //********************标题栏*********************//
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);// 启用自定义Toolbar作为ActionBar
        // 去掉Toolbar的标题
        if (getSupportActionBar() != null) {
            // 禁用标题显示
            //getSupportActionBar().setDisplayShowTitleEnabled(false);
            // 显示返回按钮
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            // 设置返回按钮的点击事件
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish(); // 释放页面资源并返回到之前的页面
                }
            });
        }
        //********************标题栏*********************//



//        //********************搜索按钮*********************//
//        imageButtonSelect = findViewById(R.id.imagebutton_select);
//        imageButtonClean = findViewById(R.id.imagebutton_clean);
//          search_input = findViewById(R.id.search_input);
//
//        final View.OnClickListener selectListener = new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // 清除所有编辑组件的焦点
//                clearAllEditTextFocus();
//                //查询按钮
//                if (v.getId() == R.id.imagebutton_select) {
//                    String searchContent = search_input.getText().toString();
//                    searchItems(searchContent);
//                }
//                //清除按钮
//                else if (v.getId() == R.id.imagebutton_clean) {
//                    String searchContent = "";
//                    search_input.setText(searchContent);
//                    searchItems(searchContent);
//                }
//            }
//
//            private void searchItems(String query) {
//                // 遍历表格的每一行
//                for (int i = 1; i < tab_dynamics.getChildCount(); i++) { // 从 1 开始，跳过表头
//                    TableRow tableRow = (TableRow) tab_dynamics.getChildAt(i);
//                    String modbusAddress = ((TextView) tableRow.getChildAt(0)).getText().toString();
//                    String bit = ((TextView) tableRow.getChildAt(1)).getText().toString();
//                    String functingName = ((TextView) tableRow.getChildAt(2)).getText().toString();
//
//                    // 根据查询条件判断行的可见性
//                    if (!query.isEmpty()){
//                        if (modbusAddress.contains(query) || bit.contains(query) || functingName.contains(query)) {
//                            tableRow.setVisibility(View.VISIBLE); // 显示匹配的行
//                        } else {
//                            tableRow.setVisibility(View.GONE); // 隐藏不匹配的行
//                        }
//                    }else{
//                        tableRow.setVisibility(View.VISIBLE); // 显示所有行
//                    }
//                }
//            }
//        };
//        imageButtonSelect.setOnClickListener(selectListener);
//        imageButtonClean.setOnClickListener(selectListener);
//        //********************搜索按钮*********************//

        //********************连接、断开、读取、停止读取按钮**********//
        button_connect.setEnabled(true);
        ViewCompat.setBackgroundTintList(button_connect, ColorStateList.valueOf(getResources().getColor(R.color.blue0075F6)));
        button_disconnect.setEnabled(false);
        ViewCompat.setBackgroundTintList(button_disconnect, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
        button_start_readMB.setEnabled(false);
        ViewCompat.setBackgroundTintList(button_start_readMB, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
        button_stop_readMB.setEnabled(false);
        ViewCompat.setBackgroundTintList(button_stop_readMB, ColorStateList.valueOf(getResources().getColor(R.color.gray)));

        final View.OnClickListener buttonCLickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 清除所有编辑组件的焦点
                clearAllEditTextFocus();
                //连接按钮
                if (v.getId() == R.id.button_connect) {
                    // 动态申请权限
                    checkPermissions();
                    //检查是否连接WIFI
                    checkWifiConnection();

                    connectModbusTCP();
                }
                //断开按钮
                else if(v.getId() == R.id.button_disconnect) {
                    try {
                        // 启动计时器
                        //timerHandler.removeCallbacks(readRunnable);
                        isRunning = false; // 设置标志为false，停止循环
                        //更新viewmodel
                        sharedViewModel.setIsRunning(isRunning);

                        stopReadInFragment();

                        modbusMaster.disconnect();
                        //更新viewmodel
                        sharedViewModel.setModbusMaster(modbusMaster);
                        Log.d("ModbusTCP", "onClick: ModbusTCP已断开");
                    } catch (ModbusIOException e) {
                        Log.e("ModbusTCPError", "断开失败，通信异常，详细详细：ModbusIOException:"+e.getMessage());
                        // 记录错误信息到日志文件
                        LogUtils.logError(Engineering_mode_Activity.this, "断开失败，通信异常，详细详细：ModbusIOException:"+e.getMessage() + e.getMessage());
                    }catch (RuntimeException e) {
                        Log.e("ModbusTCPError", "断开失败，详细信息：RuntimeException:"+e.getMessage());
                        // 记录错误信息到日志文件
                        LogUtils.logError(Engineering_mode_Activity.this, "断开失败，详细信息：RuntimeException:" + e.getMessage());
                    } catch (Exception e) {
                        Log.e("ModbusTCPError", "断开失败，详细信息：Exception:"+e.getMessage());
                        LogUtils.logError(Engineering_mode_Activity.this, "断开失败，详细信息：Exception:" + e.getMessage());
                    }
                    button_connect.setEnabled(true);
                    ViewCompat.setBackgroundTintList(button_connect, ColorStateList.valueOf(getResources().getColor(R.color.blue0075F6)));
                    button_disconnect.setEnabled(false);
                    ViewCompat.setBackgroundTintList(button_disconnect, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                    button_start_readMB.setEnabled(false);
                    ViewCompat.setBackgroundTintList(button_start_readMB, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                    button_stop_readMB.setEnabled(false);
                    ViewCompat.setBackgroundTintList(button_stop_readMB, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                }
                //开始读取按钮
                else if(v.getId() == R.id.button_start_readMB) {
                    if (!modbusMaster.isConnected()){
                        Toast.makeText(Engineering_mode_Activity.this,"请先建立连接后再开始读取",Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 检查是否已经在运行
                    if (isRunning) {
                        Toast.makeText(Engineering_mode_Activity.this, "操作正在进行中...", Toast.LENGTH_SHORT).show();
                        return; // 如果正在运行，直接返回
                    }

                    isRunning = true; // 设置标志为true，开始循环
                    //更新viewmodel
                    sharedViewModel.setIsRunning(isRunning);

                    button_start_readMB.setEnabled(false);
                    ViewCompat.setBackgroundTintList(button_start_readMB, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                    button_stop_readMB.setEnabled(true);
                    ViewCompat.setBackgroundTintList(button_stop_readMB, ColorStateList.valueOf(getResources().getColor(R.color.blue0075F6)));

                    startReadInFragment();
                }
                //停止读取按钮
                else if(v.getId() == R.id.button_stop_readMB) {
                    // 启动计时器
                    //timerHandler.removeCallbacks(readRunnable);
                    isRunning = false; // 设置标志为false，停止循环
                    //更新viewmodel
                    sharedViewModel.setIsRunning(isRunning);

                    button_start_readMB.setEnabled(true);
                    ViewCompat.setBackgroundTintList(button_start_readMB, ColorStateList.valueOf(getResources().getColor(R.color.blue0075F6)));
                    button_stop_readMB.setEnabled(false);
                    ViewCompat.setBackgroundTintList(button_stop_readMB, ColorStateList.valueOf(getResources().getColor(R.color.gray)));

                    stopReadInFragment();
                }
            }
        };
        button_connect.setOnClickListener(buttonCLickListener);
        button_disconnect.setOnClickListener(buttonCLickListener);
        button_start_readMB.setOnClickListener(buttonCLickListener);
        button_stop_readMB.setOnClickListener(buttonCLickListener);

        //********************连接、断开、读取、停止读取按钮**********//


        //********************读写数据页面切换*********************//
        // 获取ViewModel实例
        sharedViewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        // 观察firstFragmentTabLayoutHeight的变化
        sharedViewModel.getFirstFragmentTabLayoutHeight().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer height) {
                firstFragmentTabLayoutHeight=height;
            }
        });
        // 观察firstFragmentTabLayoutHeight的变化
        sharedViewModel.getSecondFragmentTabLayoutHeight().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer height) {
                secondFragmentTabLayoutHeight=height;
            }
        });
        // 观察firstFragmentTabLayoutHeight的变化
        sharedViewModel.getThirdFragmentTabLayoutHeight().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer height) {
                thirdFragmentTabLayoutHeight=height;
            }
        });
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // 页面滚动时的回调
//                触发条件：当页面正在被滑动时。这个方法会在页面转换过程中持续被调用。
//                参数解释：
//                position：当前页面的位置（从左到右）。
//                positionOffset：偏移量，范围在0.0到1.0之间，表示当前页面滑动的程度。
//                positionOffsetPixels：偏移的像素数量。

                viewPager.post(new Runnable() {
                    @Override
                    public void run() {
                        int currentHeight = viewPager.getHeight();

                        ViewGroup.LayoutParams params = viewPager.getLayoutParams();
                        switch (position){
                            case 0:
                                if(currentHeight!=firstFragmentTabLayoutHeight){
                                    params.height = firstFragmentTabLayoutHeight;
                                    viewPager.setLayoutParams(params);
                                    viewPager.invalidate();
                                    viewPager.requestLayout();
                                }
                                break;
                            case 1:
                                if(currentHeight!=secondFragmentTabLayoutHeight){
                                    params.height = secondFragmentTabLayoutHeight;
                                    viewPager.setLayoutParams(params);
                                    viewPager.invalidate();
                                    viewPager.requestLayout();
                                }
                                break;
                            case 2:
                                if(currentHeight!=thirdFragmentTabLayoutHeight){
                                    params.height = thirdFragmentTabLayoutHeight;
                                    viewPager.setLayoutParams(params);
                                    viewPager.invalidate();
                                    viewPager.requestLayout();
                                }
                                break;
                        }

                    }
                });
            }

            @Override
            public void onPageSelected(int position) {
//                触发条件：当用户选择了一个新的页面时（即滑动完成并停留在新的页面上）。
//                参数解释：
//                position：新选中页面的位置。这个位置在用户完成滑动并选择某个页面后被调用
                // 页面选中时的回调
                // 在这里可以执行你想要的操作，比如更新UI或触发事件

                // 获取 FragmentManager
                FragmentManager fragmentManager = getSupportFragmentManager();
                List<Fragment> fragments = fragmentManager.getFragments();

                switch (position) {
                    case 0:
                        // 处理第一个页面
                        // 遍历 Fragment 列表，找到对应的 Fragment 并调用 StartRead 方法
                        for (Fragment fragment : fragments) {
                            if (fragment instanceof FirstFragment) {
                                FirstFragment firstFragment = (FirstFragment) fragment;
                                firstFragment.StartRead();
                            }else if (fragment instanceof SecondFragment){
                                SecondFragment secondFragment = (SecondFragment) fragment;
                                secondFragment.StopRead(); // 停止旧的读取
                                //break; // 找到后可以结束循环
                            }else if (fragment instanceof ThirdFragment){
                                ThirdFragment thirdFragment = (ThirdFragment) fragment;
                                thirdFragment.StopRead(); // 停止旧的读取
                                //break; // 找到后可以结束循环
                            }
                        }
                        break;
                    case 1:
                        // 处理第二个页面
                        // 遍历 Fragment 列表，找到对应的 Fragment 并调用 StartRead 方法
                        for (Fragment fragment : fragments) {
                            if (fragment instanceof FirstFragment) {
                                FirstFragment firstFragment = (FirstFragment) fragment;
                                firstFragment.StopRead();
                                //break; // 找到后可以结束循环
                            }else if (fragment instanceof SecondFragment){
                                SecondFragment secondFragment = (SecondFragment) fragment;
                                secondFragment.StartRead();
                            }else if (fragment instanceof ThirdFragment){
                                ThirdFragment thirdFragment = (ThirdFragment) fragment;
                                thirdFragment.StopRead();
                            }
                        }
                        break;
                    case 2:
                        // 处理第二个页面
                        // 遍历 Fragment 列表，找到对应的 Fragment 并调用 StartRead 方法
                        for (Fragment fragment : fragments) {
                            if (fragment instanceof FirstFragment) {
                                FirstFragment firstFragment = (FirstFragment) fragment;
                                firstFragment.StopRead();
                                //break; // 找到后可以结束循环
                            }else if (fragment instanceof SecondFragment){
                                SecondFragment secondFragment = (SecondFragment) fragment;
                                secondFragment.StopRead();
                            }else if (fragment instanceof ThirdFragment){
                                ThirdFragment thirdFragment = (ThirdFragment) fragment;
                                thirdFragment.StartRead();
                            }
                        }
                        break;
                }

                viewPager.post(new Runnable() {
                    @Override
                    public void run() {
                        ViewGroup.LayoutParams params = viewPager.getLayoutParams();
                        if (position == 0) {
                            params.height = firstFragmentTabLayoutHeight;
                        } else if (position == 1) {
                            params.height = secondFragmentTabLayoutHeight;
                        } else if (position == 2) {
                            params.height = thirdFragmentTabLayoutHeight;
                        }
                        viewPager.setLayoutParams(params);
                        viewPager.invalidate();
                        viewPager.requestLayout();
                    }
                });
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // 页面滚动状态改变时的回调
//                触发条件：当页面的滑动状态发生改变时。
//                参数解释：
//                state：页面滑动状态，可以是以下三种：
//                0 (SCROLL_STATE_IDLE)：空闲状态，表示没有滑动。
//                1 (SCROLL_STATE_DRAGGING)：拖动状态，表示用户正在拖动页面。
//                2 (SCROLL_STATE_SETTLING)：稳定状态，表示页面正在从一个位置滑动到另一个位置，正在加载。

            }
        });
        tabLayout.setupWithViewPager(viewPager);
        //********************读写数据页面切换*********************//
    }

    private void startReadInFragment() {
        // 获取 FragmentManager
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragments = fragmentManager.getFragments();
        //获取当前viewpager的页面
        int currentItem = viewPager.getCurrentItem();
        // 遍历 Fragment 列表，找到对应的 Fragment 并调用 StartRead 方法

        if (currentItem == 0){
            for (Fragment fragment : fragments) {
                if (fragment instanceof FirstFragment) {
                    FirstFragment firstFragment = (FirstFragment) fragment;
                    // 现在可以使用 firstFragment 实例
                    firstFragment.StartRead();
                    //break; // 找到后可以结束循环
                }
            }
        }else if (currentItem == 1){
            for (Fragment fragment : fragments) {
                if (fragment instanceof SecondFragment) {
                    SecondFragment secondFragment = (SecondFragment) fragment;
                    // 现在可以使用 secondFragment 实例
                    secondFragment.StartRead();
                    //break; // 找到后可以结束循环
                }
            }
        }
        else if (currentItem == 2){
            for (Fragment fragment : fragments) {
                if (fragment instanceof ThirdFragment) {
                    ThirdFragment thirdFragment = (ThirdFragment) fragment;
                    // 现在可以使用 thirdFragment 实例
                    thirdFragment.StartRead();
                    //break; // 找到后可以结束循环
                }
            }
        }
    }

    private void stopReadInFragment() {
        // 获取 FragmentManager
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragments = fragmentManager.getFragments();
        //获取当前viewpager的页面
        int currentItem = viewPager.getCurrentItem();
        // 遍历 Fragment 列表，找到对应的 Fragment 并调用 StartRead 方法

        if (currentItem == 0){
            for (Fragment fragment : fragments) {
                if (fragment instanceof FirstFragment) {
                    FirstFragment firstFragment = (FirstFragment) fragment;
                    // 现在可以使用 firstFragment 实例
                    firstFragment.StopRead();
                }
            }
        }else if (currentItem == 1){
            for (Fragment fragment : fragments) {
                if (fragment instanceof SecondFragment) {
                    SecondFragment secondFragment = (SecondFragment) fragment;
                    // 现在可以使用 secondFragment 实例
                    secondFragment.StopRead();
                }
            }
        }else if (currentItem == 2){
            for (Fragment fragment : fragments) {
                if (fragment instanceof ThirdFragment) {
                    ThirdFragment thirdFragment = (ThirdFragment) fragment;
                    // 现在可以使用 secondFragment 实例
                    thirdFragment.StopRead();
                }
            }
        }
    }

    private void connectModbusTCP() {
        ipAddress = input_IP.getText().toString();
        port = Integer.parseInt(input_port.getText().toString().isEmpty() ? "0" : input_port.getText().toString());
        slaveAddress = Integer.parseInt(input_slave_address.getText().toString().isEmpty()? "-1" : input_slave_address.getText().toString());
        sharedViewModel.setInput_slave_address(String.valueOf(slaveAddress));
        if (ipAddress.isEmpty() || port == 0 || slaveAddress<0) {
            Toast.makeText(Engineering_mode_Activity.this,"请输入正确的IP地址端口和从站地址",Toast.LENGTH_LONG).show();
            return;
        }

        Thread connectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 设置主机TCP参数
                    tcpParameters = new TcpParameters();
                    // 设置TCP的ip地址
                    tcpParameters.setHost(InetAddress.getByName(ipAddress));
                    // TCP设置端口，这里设置是默认端口503
                    tcpParameters.setPort(port);
                    // TCP设置长连接
                    tcpParameters.setKeepAlive(true);
                    // 创建一个主机
                    modbusMaster = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
                    //设置递增的事务ID
                    Modbus.setAutoIncrementTransactionId(true);
                    if (!modbusMaster.isConnected()) {
                        modbusMaster.connect();// 开启连接
                    }

                    Log.d("ModbusTCP", "ModbusTCP连接成功");
                    // 连接成功后调用回调
                    if (modbusMasterCallback!= null) {
                        modbusMasterCallback.onConnectSuccess(modbusMaster);
                        //更新viewmodel
                        sharedViewModel.setModbusMaster(modbusMaster);
                    }
                } catch (ModbusIOException e) {
                    Log.e("ModbusTCPError", "连接失败，请检查网络及设备状态，详细信息：ModbusIOException:"+e.getMessage());
                    // 连接失败后调用回调
                    if (modbusMasterCallback!= null) {
                        modbusMasterCallback.onConnectModbusIOException(e);
                    }
                } catch (RuntimeException e) {
                    Log.e("ModbusTCPError", "连接失败,RuntimeException:"+e.getMessage());
                    // 连接失败后调用回调
                    if (modbusMasterCallback!= null) {
                        modbusMasterCallback.onConnectRuntimeException(e);
                    }
                    throw e;
                } catch (Exception e) {
                    Log.e("ModbusTCPError", "连接失败,Exception:"+e.getMessage());
                    // 连接失败后调用回调
                    if (modbusMasterCallback!= null) {
                        modbusMasterCallback.onConnectException(e);
                    }
                }
            }
        });
        connectThread.start();
    }

    // 定义一个方法来清除所有 EditText 的焦点
    private void clearAllEditTextFocus() {
        input_slave_address.clearFocus();
        input_IP.clearFocus();
        input_IP.setFocusable(false);
        input_IP.setFocusableInTouchMode(true);

        input_port.clearFocus();

        // 隐藏键盘
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

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
                        modbusUtils.data_format_order = data_format_order;

                        JSONObject jsonObject_data = new JSONObject(jsonData);
                        JSONArray dataArray = jsonObject_data.getJSONArray("数据");
                        return dataArray; // 返回解析后的 JSON 数组
                    })
                    .subscribeOn(Schedulers.io()) // 在 IO 线程上处理 JSON 解析
                    .observeOn(AndroidSchedulers.mainThread()) // 切换到主线程进行 UI 操作
                    .subscribe(dataArray -> {
                        // 创建一个水平滚动视图
                        hori_dynamics = new HorizontalScrollView(this);
                        hori_dynamics.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,1.0f)); // 高度可以根据内容调整
//                        hori_dynamics.setBackground(new ColorDrawable(Color.BLACK));
//                        hori_dynamics.setBackgroundColor(Color.YELLOW);

                        // 创建 TableLayout 组件
                        tab_dynamics = new TableLayout(this);
                        tab_dynamics.setLayoutParams(new TableLayout.LayoutParams(
                                TableLayout.LayoutParams.MATCH_PARENT,
                                TableLayout.LayoutParams.WRAP_CONTENT));
                        tab_dynamics.setStretchAllColumns(true);

                        // 添加表格标题
                        TableRow headerRow = new TableRow(this);
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
                        float scale = this.getResources().getDisplayMetrics().density;
                        int heightInPx = (int) (heightInDp * scale + 0.5f); // 将dp转换为px

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
                                TableRow tableRow = new TableRow(this);
                                tableRow.setLayoutParams(new TableRow.LayoutParams(
                                        TableRow.LayoutParams.MATCH_PARENT,
                                        heightInPx,1.0f));
                                tableRow.setBackgroundResource(R.drawable.table_border); // 设置边框背景
                                tableRow.setMinimumHeight(heightInPx); // 设置最小高度

                                // MODBUS地址
                                tableRow.addView(createTextView(modbusAddress));
                                //位
                                tableRow.addView(createTextView("("+bit+")"));

                                // 功能名称
                                TextView functingNameView = createTextView(functingName);
                                TableRow.LayoutParams params_functingName = new TableRow.LayoutParams(
                                        TableRow.LayoutParams.WRAP_CONTENT,
                                        TableRow.LayoutParams.WRAP_CONTENT
                                );
                                params_functingName.gravity = Gravity.CENTER_VERTICAL; // 设置为垂直居中
                                functingNameView.setLayoutParams(params_functingName); // 应用参数
                                tableRow.addView(functingNameView);

                                // 隐藏“读写类型”列
                                TextView readWriteView = createTextView(readWrite);
                                readWriteView.setVisibility(View.GONE); // 隐藏该视图
                                tableRow.addView(readWriteView);
                                // 隐藏“单位”列
                                TextView unitView = createTextView(unit);
                                unitView.setVisibility(View.GONE); // 隐藏该视图
                                tableRow.addView(unitView);
                                // 显示“值”列
                                tableRow.addView(createTextView(value));
                                // 添加编辑按钮，仅在读写类型为 R/W 时添加
                                if (readWrite.equals("R/W") && !unit.equalsIgnoreCase("bit")) {
                                    addEditButton(tableRow, modbusAddress, bit, unit, value, buttonType);
                                } else if(readWrite.equals("R/W") && unit.equalsIgnoreCase("bit")) {
                                    if(unit.equalsIgnoreCase("bit")){
                                        // 创建开关样式的 ToggleButton
                                        ToggleButton toggleButton = new ToggleButton(this);
                                        toggleButton.setTextOff("OFF"); // 关闭时的文本
                                        toggleButton.setTextOn("ON");  // 打开时的文本
                                        toggleButton.setChecked(false); // 初始化为关闭状态
                                        toggleButton.setTag(buttonType); // 设置按钮类型
                                        toggleButton.setEnabled(true); // 禁用
                                        // 设置背景色调
                                        ColorStateList colorStateList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)); // 使用红色作为示例
                                        toggleButton.setBackgroundTintList(colorStateList);

                                        toggleButton.setTextAppearance(R.style.pingfang_bold); // 应用样式
                                        // 添加到表格行
                                        tableRow.addView(toggleButton);
                                        setToggleButtonTouchListener(modbusAddress, bit, toggleButton,buttonType);
                                    }
                                }else {
                                    if(unit.equalsIgnoreCase("bit")){
                                        // 创建开关样式的 ToggleButton
                                        ToggleButton toggleButton = new ToggleButton(this);
                                        toggleButton.setTextOff("OFF"); // 关闭时的文本
                                        toggleButton.setTextOn("ON");  // 打开时的文本
                                        toggleButton.setChecked(false); // 初始化为关闭状态
                                        toggleButton.setTag(buttonType); // 设置按钮类型
                                        toggleButton.setEnabled(false); // 禁用

                                        // 设置背景色调
                                        ColorStateList colorStateList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)); // 使用红色作为示例
                                        toggleButton.setBackgroundTintList(colorStateList);

                                        toggleButton.setTextAppearance(R.style.pingfang_bold); // 应用字体样式
                                        // 添加到表格行
                                        tableRow.addView(toggleButton);
                                    }
                                    else{
                                        ToggleButton toggleButton = new ToggleButton(this);
                                        toggleButton.setVisibility(View.GONE);
                                        // 添加到表格行
                                        tableRow.addView(toggleButton);
                                    }
                                }

                                // 隐藏“缩放因子”列
                                TextView saleFactorView = createTextView(scaleFactor);
                                saleFactorView.setVisibility(View.GONE); // 隐藏该视图
                                saleFactorView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT));
                                tableRow.addView(saleFactorView);

                                tab_dynamics.addView(tableRow); // 添加到表格中
                            }
                        }

                        // 更新 UI
                        if (container != null) {
                             container.removeAllViews();
                             //同样设置 tableLayout 的宽度占满
                             hori_dynamics.addView(tab_dynamics);
                             container.addView(hori_dynamics);
                        }

                        // 保存数据到 SharedPreferences
                        preferences = getSharedPreferences("Preference", 0);
                        preferences.edit().putString("savedTableData_Engineering_mode", jsonData).apply();

                        // 保存表格结构
                        saveTableStructure(dataArray);
                    }, throwable -> {
                        // 错误处理
                        Log.e("JSON", "解析JSON失败: " + throwable.getMessage());
                    });
        } else {
            Log.e("JSON", "加载JSON数据失败，jsonData为空");
        }
            // 记录方法执行完毕的时间戳
            long endTime = System.currentTimeMillis();
            // 计算并打印执行时间
            long duration = endTime - startTime;
            Log.i("JSON", "loadJsonData() 总执行时间：" + duration + "ms");



        //       使用异步线程
//        executorService.execute(() -> {
//            // 记录方法开始执行的时间戳
//            long startTime = System.currentTimeMillis();
//            // 使用外部类的引用来获取 Context
//            Context context = MainActivity.this; // 替换为你的 Activity 名称
//
//            if (jsonData != null) {
//                try {
//                    JSONArray jsonArray = new JSONArray(jsonData);
//                    // 创建一个水平滚动视图
//                    HorizontalScrollView horizontalScrollView = new HorizontalScrollView(context);
//                    // 创建 TableLayout 组件
//                    tableLayout = new TableLayout(context);
//                    tableLayout.setLayoutParams(new TableLayout.LayoutParams(
//                            TableLayout.LayoutParams.MATCH_PARENT,
//                            TableLayout.LayoutParams.WRAP_CONTENT));
//
//                    // 添加表格标题
//                    TableRow headerRow = new TableRow(context);
//                    headerRow.setBackgroundResource(R.drawable.table_border); // 设置边框背景
//                    String[] headers = {"MODBUS地址", "位", "功能", "读写类型", "单位", "值"};
//                    for (String header : headers) {
//                        headerRow.addView(createTextView(header));
//                    }
//                    tableLayout.addView(headerRow);
//
//                    for (int i = 0; i < jsonArray.length(); i++) {
//                        JSONObject jsonObject = jsonArray.getJSONObject(i);
//                        String modbusAddress = jsonObject.getString("MODBUS地址");
//                        String bit = jsonObject.getString("位");
//                        String functingName = jsonObject.getString("功能");
//                        String readWrite = jsonObject.getString("读写类型");
//                        String unit= jsonObject.getString("单位");
//                        String value = jsonObject.getString("值");
//                        String buttonType = jsonObject.getString("按钮类型");
//
//                        if(!functingName.isEmpty()){
//                            TableRow tableRow = new TableRow(context);
//                            tableRow.setBackgroundResource(R.drawable.table_border); // 设置边框背景
//                            String[] tableRowDatas = {modbusAddress,bit,functingName,readWrite,unit,value};
//                            for (String tableRowData : tableRowDatas) {
//                                tableRow.addView(createTextView(tableRowData));
//                            }
//
//                            // 添加编辑按钮，仅在读写类型为 R/W 时添加
//                            if (readWrite.equals("R/W") ) {
//                                if(unit.equalsIgnoreCase("bit")) {
//                                    // 在 UI 线程中更新 UI
//                                    runOnUiThread(() -> {
//                                        // 创建开关样式的 ToggleButton
//                                        ToggleButton toggleButton = new ToggleButton(context);
//                                        toggleButton.setTextOff("OFF"); // 关闭时的文本
//                                        toggleButton.setTextOn("ON");  // 打开时的文本
//                                        toggleButton.setChecked(false); // 初始化为关闭状态
//                                        toggleButton.setBackgroundColor(Color.LTGRAY); // 设置背景颜色
//                                        toggleButton.setTag(buttonType); // 设置按钮类型
//    //                                  // 设置 ToggleButton 的大小
//    //                                  TableRow.LayoutParams params = new TableRow.LayoutParams(100, 80); // 设置宽度为200dp，高度为100dp
//    //                                  toggleButton.setLayoutParams(params); // 应用布局参数
//                                        setToggleButtonTouchListener(modbusAddress, bit, toggleButton,buttonType);
//
//                                        // 添加到表格行
//                                        tableRow.addView(toggleButton);
//                                    });
//                                }
//                                else if(!unit.equalsIgnoreCase("bit")) {
//                                    Button editButton = new Button(context);
//                                    editButton.setText("编辑");
//                                    editButton.setOnClickListener((v) ->{
//                                        // 确保在 UI 线程中执行
//                                        runOnUiThread(() -> {
//                                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
//                                            builder.setTitle("编辑值");
//
//                                            // 创建一个布局用于输入
//                                            LinearLayout layout = new LinearLayout(context);
//                                            layout.setOrientation(LinearLayout.VERTICAL);
//
//                                            EditText valueInput = new EditText(context);
//                                            valueInput.setText(value); // 设置当前值
//                                            layout.addView(valueInput);
//
//                                            builder.setView(layout);
//
//                                            builder.setPositiveButton("确认", (dialog, which) -> {
//                                                String newValue = valueInput.getText().toString();
//                                                // 修改表格中对应的行的值
//                                                ((TextView) tableRow.getChildAt(5)).setText(newValue); // 值在第6列
//
////                                                Thread edit = new Thread(new Runnable() {
////                                                    @Override
////                                                    public void run() {
//                                                executorService.execute(() -> {
//                                                    //发送数据到保持寄存器
//                                                    // 判断newValue是否是float类型
//                                                    try {
//                                                        if (input_slave_address != null) {
//                                                            slaveAddress = Integer.parseInt(input_slave_address.getText().toString());
//                                                            int address = Integer.parseInt(modbusAddress) - 40001;
//                                                            // 判断是否为浮点数
//                                                            if (unit.equals("Float")) {
//                                                                float floatValue = Float.parseFloat(newValue);
//                                                                int[] floatToRegister = floatToRegisters(floatValue); // 将浮点值转换为小端格式的两个寄存器
//                                                                // 写入两个保持寄存器
//                                                                modbusMaster.writeMultipleRegisters(slaveAddress, address, floatToRegister);
//                                                            } else {
//                                                                int intValue = Integer.parseInt(newValue);
//                                                                // 写入单个保持寄存器
//                                                                modbusMaster.writeSingleRegister(slaveAddress, address, intValue);
//                                                            }
//                                                        }
//                                                    } catch (NumberFormatException e) {
//                                                        Log.e("ModbusTCPError", "ModbusTCP写入失败,NumberFormatException:" + e.getMessage());
//                                                        // 读取失败后调用回调
//                                                        if (modbusMasterCallback != null) {
//                                                            modbusMasterCallback.onWriteFailure_NumberFormatException(e);
//                                                        }
//                                                    } catch (ModbusProtocolException e) {
//                                                        Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusProtocolException:" + e.getMessage());
//                                                        // 读取失败后调用回调
//                                                        if (modbusMasterCallback != null) {
//                                                            modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
//                                                        }
//                                                    } catch (ModbusNumberException e) {
//                                                        Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusNumberException:" + e.getMessage());
//                                                        if (modbusMasterCallback != null) {
//                                                            modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
//                                                        }
//                                                    } catch (ModbusIOException e) {
//                                                        Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusIOException:" + e.getMessage());
//                                                        if (modbusMasterCallback != null) {
//                                                            modbusMasterCallback.onWriteFailure_ModbusIOException(e);
//                                                        }
//                                                    } catch (Exception e) {
//                                                        Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusException:" + e.getMessage());
//                                                        if (modbusMasterCallback != null) {
//                                                            modbusMasterCallback.onWriteFailure_Exception(e);
//                                                        }
//                                                    }
////                                                    }
////                                                });
////                                                edit.start();
//                                                });
//                                            });
//
//                                            builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
//
//                                            builder.show();
//                                        });
//                                    });
//                                    tableRow.addView(editButton);
//                                }
//                            }
//                            else {
//                                // 在 UI 线程中更新 UI
//                                runOnUiThread(() -> {
//                                    // 创建开关样式的 ToggleButton
//                                    ToggleButton toggleButton = new ToggleButton(context);
//                                    toggleButton.setTextOff("OFF"); // 关闭时的文本
//                                    toggleButton.setTextOn("ON");  // 打开时的文本
//                                    toggleButton.setChecked(false); // 初始化为关闭状态
//                                    toggleButton.setBackgroundColor(Color.LTGRAY); // 设置背景颜色
//                                    toggleButton.setTag(buttonType); // 设置按钮类型
//                                    toggleButton.setEnabled(false); // 禁用
//                                    // 添加到表格行
//                                    tableRow.addView(toggleButton);
//                                });
//                            }
//                            // 在 UI 线程中更新 UI
//                            runOnUiThread(() -> {
//                                tableLayout.addView(tableRow); // 添加到表格中
//                            });
//                        }
//                    }
//                    // 在 UI 线程中更新 UI
//                    runOnUiThread(() -> {
//                        if (container != null) {
//                        //清空原有内容
//                        container.removeAllViews();
//                        // 将表格添加到水平滚动视图
//                        horizontalScrollView.addView(tableLayout);
//                        // 将表格添加到容器中
//                        container.addView(horizontalScrollView);
//                    }
//                    });
//
//
//
//                    // 保存当前生成的表格数据到 SharedPreferences
//                    //preferences = getSharedPreferences("Preference",0);
//                    preferences.edit().putString("savedTableData_Engineering_mode", jsonData).apply();
//
//                    // 保存表格结构
//                    JSONArray savedStructure = new JSONArray();
//                    for (int i = 0; i < jsonArray.length(); i++) {
//                        JSONObject jsonObject = jsonArray.getJSONObject(i);
//                        JSONObject structObject = new JSONObject();
//                        structObject.put("MODBUS地址", jsonObject.getString("MODBUS地址"));
//                        structObject.put("位", jsonObject.getString("位"));
//                        structObject.put("功能", jsonObject.getString("功能"));
//                        structObject.put("读写类型", jsonObject.getString("读写类型"));
//                        structObject.put("单位", jsonObject.getString("单位"));
//                        structObject.put("值", jsonObject.getString("值"));
//                        savedStructure.put(structObject);
//                    }
//                }
//                catch (JSONException e) {
//                    Log.e("JSON", "解析JSON失败,JSONException:"+e.getMessage());
//                    //Toast.makeText(this, "解析JSON失败", Toast.LENGTH_SHORT).show();
//                }
//                catch (Exception e){
//                    Log.e("JSON", "解析JSON失败,Exception:"+e.getMessage());
//                    //Toast.makeText(this, "解析JSON失败", Toast.LENGTH_SHORT).show();
//                }
//            }
//            else {
//                Log.e("JSON", "加载JSON数据失败，jsonData为空");
//                //Toast.makeText(this, "加载JSON数据失败，jsonData为空", Toast.LENGTH_SHORT).show();
//            }
//
//            // 记录方法执行完毕的时间戳
//            long endTime = System.currentTimeMillis();
//            // 计算并打印执行时间
//            long duration = endTime - startTime;
//            Log.i("JSON", "loadJsonData() 总执行时间：" + duration + "ms");
//        });
    }

    // 辅助方法：添加编辑按钮
    private void addEditButton(TableRow tableRow, String modbusAddress, String bit, String unit, String value, String buttonType) {
        FButton editFButton = new FButton(this);
        editFButton.setText("编辑");
        // 设置背景色调
        ColorStateList colorStateList_green = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green00bc12)); // 使用红色作为示例
        editFButton.setBackgroundTintList(colorStateList_green);
        editFButton.setTextAppearance(R.style.pingfang_bold);

        editFButton.setOnClickListener((v) -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("编辑值");

            // 创建一个布局用于输入
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);

            // 从tableRow获取当前值
            String currentValue = ((TextView) tableRow.getChildAt(5)).getText().toString();
            EditText valueInput = new EditText(this);
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
                handleValueWrite(modbusAddress, input_slave_address, unit, newValue,scaleFactor_float);
            });

            builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
            builder.show();
        });
        tableRow.addView(editFButton);
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

    // 辅助方法：处理值的写入
    private void handleValueWrite(String modbusAddress, EditText inputSlaveAddress, String unit, String newValue,Float scaleFactor_float) {
        Single.fromCallable(() -> {
                    int slaveAddress = Integer.parseInt(inputSlaveAddress.getText().toString());
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
                    Log.e("ModbusTCPError", "ModbusTCP写入失败: " + throwable.getMessage());
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
            Log.e("JSON", "保存表格结构失败: " + e.getMessage());
        }

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
                        ColorStateList colorStateList_green = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)); // 使用红色作为示例
                        toggleButton.setBackgroundTintList(colorStateList_green);

                        Thread down = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //发送数据到保持寄存器
                                // 判断newValue是否是float类型
                                try {
                                    slaveAddress=Integer.parseInt( input_slave_address.getText().toString());
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
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                    }
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }catch (Exception e){
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
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
                        ColorStateList colorStateList_gray = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray)); // 使用红色作为示例
                        toggleButton.setBackgroundTintList(colorStateList_gray);

                        Thread cancel = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //发送数据到保持寄存器
                                // 判断newValue是否是float类型
                                try {
                                    slaveAddress=Integer.parseInt( input_slave_address.getText().toString());
                                    int address=Integer.parseInt( modbusAddress)-40001;

                                    // 读取当前值
                                    int currentValue = modbusMaster.readHoldingRegisters(slaveAddress, address, 1)[0];

                                    // 设置当前位为0
                                    int bitPosition = Integer.parseInt(bit); // 当前位的位置
                                    int newValue = currentValue & ~(1 << bitPosition); // 清除当前位（设置为0）
                                    // 写入更改后的值
                                    modbusMaster.writeSingleRegister(slaveAddress, address, newValue);
                                } catch (NumberFormatException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                    }
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                    // 写入失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }catch (Exception e){
                                    Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
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
                    ColorStateList colorStateList_green = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)); // 使用红色作为示例
                    toggleButton.setBackgroundTintList(colorStateList_green);

                    Thread checked = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //发送数据到保持寄存器
                            // 判断newValue是否是float类型
                            try {
                                slaveAddress=Integer.parseInt( input_slave_address.getText().toString());
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
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                }
                            } catch (ModbusProtocolException e) {
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                }
                            } catch (ModbusNumberException e) {
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                }
                            } catch (ModbusIOException e) {
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                }
                            }catch (Exception e){
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_Exception(e);
                                }
                            }
                        }
                    });
                    checked.start();
                } else {
                    // 设置背景色调
                    ColorStateList colorStateList_gray = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray)); // 使用红色作为示例
                    toggleButton.setBackgroundTintList(colorStateList_gray);
                    Thread unchecked = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //发送数据到保持寄存器
                            // 判断newValue是否是float类型
                            try {
                                slaveAddress=Integer.parseInt( input_slave_address.getText().toString());
                                int address=Integer.parseInt( modbusAddress)-40001;

                                // 读取当前值
                                int currentValue = modbusMaster.readHoldingRegisters(slaveAddress, address, 1)[0];

                                // 设置当前位为0
                                int bitPosition = Integer.parseInt(bit); // 当前位的位置
                                int newValue = currentValue & ~(1 << bitPosition); // 清除当前位（设置为0）
                                // 写入更改后的值
                                modbusMaster.writeSingleRegister(slaveAddress, address, newValue);
                            } catch (NumberFormatException e) {
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                }
                            } catch (ModbusProtocolException e) {
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                // 读取失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                }
                            } catch (ModbusNumberException e) {
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                }
                            } catch (ModbusIOException e) {
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                }
                            }catch (Exception e){
                                Log.e("ModbusTCPError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
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

    // 创建 TextView 的方法
    private TextView createTextView(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextAppearance(R.style.pingfang_bold); // 应用样式
        return textView;
    }


    //检查权限
    private void checkPermissions() {
        int PERMISSION_REQUEST_CODE_Wifi = 1;
        // 检查WiFi状态权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // 权限未被授予，进行请求
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CHANGE_WIFI_STATE},
                    PERMISSION_REQUEST_CODE_Wifi);
        } 
        else {
            // 权限已被授予，可以执行相关操作
            Log.d("checkPermissions","WiFi权限已被授予");
            //Toast.makeText(this, "WiFi权限已被授予", Toast.LENGTH_SHORT).show();
        }
    }

    public interface ModbusMasterCallback {
        //连接
        void onConnectSuccess(ModbusMaster master);
        void onConnectModbusIOException(Exception e);
        void onConnectRuntimeException(Exception e);
        void onConnectException(Exception e);
        //读取
        void onReadSuccess(ModbusMaster master);
        void onReadFailure_ModbusProtocolException(Exception e);
        void onReadFailure_ModbusNumberException(Exception e);
        void onReadFailure_ModbusIOException(Exception e);
        void onReadFailure_Exception(Exception e);
         //写入
        void onWriteFailure_ModbusProtocolException(Exception e);
        void onWriteFailure_ModbusNumberException(Exception e);
        void onWriteFailure_ModbusIOException(Exception e);
        void onWriteFailure_NumberFormatException(Exception e);
        void onWriteFailure_IllegalArgumentException(Exception e);
        Void onWriteFailure_TimeoutException(Exception e);
        void onWriteFailure_Exception(Exception e);
    }

    private ModbusMasterCallback modbusMasterCallback = new ModbusMasterCallback() {
        @Override
        public void onConnectSuccess(ModbusMaster master) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "连接成功" ,Toast.LENGTH_LONG).show();
                    //Toast.makeText(MainActivity.this, "ModbusTCP连接成功",Toast.LENGTH_SHORT).show();
                    button_connect.setEnabled(false);
                    ViewCompat.setBackgroundTintList(button_connect, ColorStateList.valueOf(getResources().getColor(R.color.gray)));
                    button_disconnect.setEnabled(true);
                    ViewCompat.setBackgroundTintList(button_disconnect, ColorStateList.valueOf(getResources().getColor(R.color.blue0075F6)));
                    button_start_readMB.setEnabled(true);
                    ViewCompat.setBackgroundTintList(button_start_readMB, ColorStateList.valueOf(getResources().getColor(R.color.blue0075F6)));


                    //持久化保存IP、端口、从机地址
                    //持久化保存IP、端口、从机地址
                    preferences = getSharedPreferences("Preference",0);
                    preferences.edit().putString("ipAddress",input_IP.getText().toString()).apply();
                    preferences.edit().putString("port_Engineering_mode",input_port.getText().toString()).apply();
                    preferences.edit().putString("slaveAddress",input_slave_address.getText().toString()).apply();
                }
            });
        }
        @Override
        public void onConnectModbusIOException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "连接失败，请检查网络及设备状态，详细信息：ModbusIOException:" + e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"连接失败，请检查网络及设备状态，详细信息：ModbusIOException:"+e.getMessage());
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    button_start_readMB.setEnabled(false);
                    button_stop_readMB.setEnabled(false);
                }
            });
        }
        @Override
        public void onConnectRuntimeException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "连接失败,RuntimeException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"连接失败,RuntimeException:"+e.getMessage());
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    button_start_readMB.setEnabled(false);
                    button_stop_readMB.setEnabled(false);
                }
            });
        }
        @Override
        public void onConnectException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "连接失败，Exception:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"连接失败，Exception:"+e.getMessage());
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    button_start_readMB.setEnabled(false);
                    button_stop_readMB.setEnabled(false);
                }
            });
        }

        @Override
        public void onReadSuccess(ModbusMaster master) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "读取成功",Toast.LENGTH_LONG).show();
                    readFaliedCount=0;
                }
            });
        }
        @Override
        public void onReadFailure_ModbusProtocolException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "读取失败，数值异常，异常信息："+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"读取失败，数值异常，异常信息："+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Engineering_mode_Activity.this, "读取失败，详细信息：Exception:"+e.getMessage(),Toast.LENGTH_LONG).show();
                        LogUtils.logError(Engineering_mode_Activity.this,"读取失败，详细信息：Exception:"+e.getMessage());

                        readFaliedCount++;
                        if(readFaliedCount>=3){
                            isRunning=false;
                            //更新viewmodel
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
            }
        }

        @Override
        public void onWriteFailure_ModbusProtocolException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "写入失败，协议异常，详细信息："+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"写入失败，协议异常，详细信息："+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_ModbusNumberException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "写入失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"写入失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_ModbusIOException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "写入失败，写入超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"写入失败，写入超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_NumberFormatException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "写入失败，数字格式异常，详细信息：NumberFormatException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"写入失败，数字格式异常，详细信息：NumberFormatException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_IllegalArgumentException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "写入失败，参数无效，详细信息：IllegalArgumentException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"写入失败，参数无效，详细信息：IllegalArgumentException:"+e.getMessage());
                }
            });
        }
        @Override
        public Void onWriteFailure_TimeoutException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "写入失败，不符合预期的操作，详细信息：RuntimeException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"写入失败，不符合预期的操作，详细信息：RuntimeException:"+e.getMessage());
                }
            });
            return null;
        }
        @Override
        public void onWriteFailure_Exception(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Engineering_mode_Activity.this, "写入失败，详细信息：Exception:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    LogUtils.logError(Engineering_mode_Activity.this,"写入失败，详细信息：Exception:"+e.getMessage());
                }
            });
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //请求权限标志位
        int PERMISSION_REQUEST_CODE_Wifi = 1;
        int PERMISSION_REQUEST_CODE_File = 100;
        //请求WIFI权限的回调
        if (requestCode == PERMISSION_REQUEST_CODE_Wifi) {
            // 如果请求被取消了，那么结果数组是空的
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予
                Toast.makeText(this, "WiFi权限已被授予", Toast.LENGTH_SHORT).show();
                // 这里可以添加需要WiFi权限的操作
            } else {
                // 权限被拒绝
                Toast.makeText(this, "WiFi权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
        //请求文件权限的回调
        if (requestCode == PERMISSION_REQUEST_CODE_File) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
            else {
                // 检查用户是否选择了不再询问
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
                    // 用户拒绝了权限，但没有勾选不再询问
                    Toast.makeText(this, "权限被拒绝，您可以重新尝试请求权限", Toast.LENGTH_SHORT).show();
                }
                else{
                    // 用户选择了不再询问
                    Toast.makeText(this, "权限被永久拒绝，请在设置中手动启用", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            }
        }
    }

    private void checkWifiConnection() {
        //保证WIFI连接，否则无法进行ModbusTCP通信
        if(!Objects.requireNonNull(conMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI)).isConnected())
            Toast.makeText(Engineering_mode_Activity.this,"没有连接到WiFi网络，请先连接WiFi网络",Toast.LENGTH_LONG).show();
    }

    //检查WIF是否打开
    public void checkWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if(wifiManager.isWifiEnabled())
            Toast.makeText(Engineering_mode_Activity.this,"没有打开WIFI，请打开WIFI",Toast.LENGTH_LONG).show();
    }

    //请求读取本地文件权限，如果没有权限则请求，如果已经有权限则直接读取文件内容
    private void checkAndRequestPermissions() {
        int PERMISSION_REQUEST_CODE_File = 100;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
            // 权限未被授予，进行请求
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE_File);
        } else {
            // 权限已被授予，可以执行相关操作
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*"); // 设置文件类型为任意文件
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "选择配置文件"), 1);
        }
    }

    //文件选择器返回的数据触发
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();

                // 读取文件的内容或路径
                String path = uri.getPath();
                Log.d("onActivityResult","选中的文件路径"+ path);
                // 初始化文件名称
                String fileName = null;
                // 使用ContentResolver来获取文件的详细信息
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    // 获取文件名称对应的列索引
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    // 读取文件名称
                    fileName = cursor.getString(nameIndex);
                    //Toast.makeText(this, "选中的文件名称: " + fileName, Toast.LENGTH_SHORT).show();
                    cursor.close(); // 关闭Cursor

                    //判断名称是否为config.json
                    //if(fileName.equals("config_Engineering_mode")){
                    //判断名称中是否包含config
                    if(fileName.contains("config_Engineering_mode")){
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(uri);
                            if (inputStream != null) {
                                StringBuilder stringBuilder = new StringBuilder();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    stringBuilder.append(line);
                                }
                                // 读取完成，关闭流
                                reader.close();
                                inputStream.close();

                                // 获取读取到的 JSON 内容
                                String jsonData = stringBuilder.toString();
                                // 调用处理 JSON 数据的方法
                                loadJsonData(jsonData);
                            }
                        } catch (IOException e) {
                            Log.e("JSON", "读取JSON文件失败，IOException:"+e.getMessage());
                            Toast.makeText(this, "读取JSON文件失败，IOException:", Toast.LENGTH_SHORT).show();
                        }catch (Exception e){
                            Log.e("JSON", "读取JSON文件失败，Exception:"+e.getMessage());
                            Toast.makeText(this, "读取JSON文件失败，Exception:", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else{
                        Log.e("JSON", "配置文件选择错误，请选择config.json文件");
                        Toast.makeText(this, "配置文件选择错误，请选择config.json文件", Toast.LENGTH_SHORT).show();
                    }
                }
                // 这里可以添加更多的处理逻辑，读取文件内容等
            }
        }
    }

    private String loadJSONFromAsset(String fileName) {
        String json = null;
        try {
            InputStream is = getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e("JSON", "loadJSONFromAsset失败，Exception:"+e.getMessage());
            Toast.makeText(this, "loadJSONFromAsset失败，Exception:", Toast.LENGTH_SHORT).show();
        }
        return json;
    }



    //************日志相关******************

    private String readLogFile() {
        StringBuilder logContent = new StringBuilder();
        File logFile = new File(getFilesDir(), "gzzd.txt");
        if (logFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    logContent.append(line).append("\n");
                }
            } catch (IOException e) {
                Log.e("LogReader", "读取日志文件失败: " + e.getMessage());
            }
        } else {
            logContent.append("日志文件不存在。");
        }
        return logContent.toString();
    }


    private void showLogDialog() {
        // 读取日志内容
        String logContent = readLogFile();

        // 创建一个 AlertDialog 来显示日志内容
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("日志内容");

        ScrollView scrollView = new ScrollView(this);
        // 创建一个 TextView 来显示日志内容
        TextView textView = new TextView(this);
        textView.setText(logContent);
        textView.setTextSize(14); // 设置文本大小
        textView.setPadding(16, 16, 16, 16); // 设置内边距

        // 将 TextView 添加到 ScrollView 中
        scrollView.addView(textView);

        // 将 TextView 添加到对话框中
        builder.setView(scrollView);

        // 添加关闭按钮
        builder.setPositiveButton("关闭", (dialog, which) -> dialog.dismiss());
        // 添加清除按钮
        builder.setNegativeButton("清除", (dialog, which) -> {
            clearLogFile(); // 调用清除日志文件的方法
            dialog.dismiss(); // 关闭对话框
        });
        // 显示对话框
        builder.show();
    }

    //清除日志
    private void clearLogFile() {
        // 获取日志文件的路径
        File logFile = new File(getFilesDir(), "gzzd.txt");

        // 清空日志文件
        try {
            // 使用 FileOutputStream 清空文件
            FileOutputStream fos = new FileOutputStream(logFile);
            fos.write("".getBytes()); // 写入空字符串
            fos.close(); // 关闭输出流
        } catch (IOException e) {
            e.printStackTrace(); // 处理可能发生的异常
        }
    }
}