package com.example.modbustcp_jlibmodbus.fragment;

import static com.example.modbustcp_jlibmodbus.utils.LogUtils.logError;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.example.modbustcp_jlibmodbus.R;
import com.example.modbustcp_jlibmodbus.activity.Engineering_mode_Activity;
import com.example.modbustcp_jlibmodbus.viewmodel.SharedViewModel;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThirdFragment  extends Fragment {

    //主线程
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 创建一个HandlerThread
    HandlerThread handlerThread;
    Handler threadHandler;

    // 用于控制计时器的Handler
    final Handler timerHandler = new Handler();
    Runnable readRunnable;

    // 定义一个标志变量来控制线程循环
    private volatile boolean isRunning = false;

    //JSON全局配置
    //数据格式顺序（字节转换顺序），默认为CDAB，big-endian值为ABCD，little-endian值为DCBA，big-endian-swap为BADC，little-endian-swap为CDAB，
    static String data_format_order ="CDAB";

    // 创建一个共享视图模型
    SharedViewModel sharedViewModel ;

    //Modbus
    ModbusMaster modbusMaster;
    String modbusAddress;
    //IP地址
    private String ipAddress="192.168.1.10";
    //端口
    private int port=503;
    //从站地址
    private int slaveAddress=1;

    // 存储输入寄存器和保持寄存器的地址,因页面是固定布局和对应固定modbus地址，所以这里直接写死
    int minInputAddress = 30001;
    int maxInputAddress = 30029;
    int minHoldingAddress = 40001;
    int maxHoldingAddress = 40038;

    // 定义成员变量用于存储寄存器值
    private int[] inputRegisterValues;
    private int[] holdingRegisterValues;

    //创建一个可以遍历的集合
    private List<View> viewList;
    TextView textview_gdzt,textview_jzzt,textview_gdhx,textview_gdhy,textview_gdzy,textview_gdxtzbx,textview_gdxtzby,
            textview_gdxtzbz,textview_ptzbx,textview_ptzby,textview_ptzbz,textview_gddxpc;

    //连续读取失败次数
    int readFaliedCount = 0;

    LinearLayout linearLayout_thirdfragment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 在这里加载你的 Fragment 布局
        View view = inflater.inflate(R.layout.fragment_third, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViewModel();

        initComponent(view);

        linearLayout_thirdfragment = view.findViewById(R.id.linearLayout_thirdfragment);
        linearLayout_thirdfragment.post(new Runnable() {
            @Override
            public void run() {
                int totalHeight = linearLayout_thirdfragment.getHeight();
                sharedViewModel.setThirdFragmentTabLayoutHeight(totalHeight);
                // 这里可以使用 totalHeight 进行下一步操作
            }
        });
        linearLayout_thirdfragment.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int totalHeight = linearLayout_thirdfragment.getHeight();
                sharedViewModel.setThirdFragmentTabLayoutHeight(totalHeight);
                linearLayout_thirdfragment.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    private void initComponent(View view) {
        //将需要遍历赋值的view存入集合
        viewList = new ArrayList<>();
        textview_gdzt = view.findViewById(R.id.textview_gdzt);
        textview_jzzt = view.findViewById(R.id.textview_jzzt);
        textview_gdhx = view.findViewById(R.id.textview_gdhx);
        textview_gdhy = view.findViewById(R.id.textview_gdhy);
        textview_gdzy = view.findViewById(R.id.textview_gdzy);
        textview_gdxtzbx = view.findViewById(R.id.textview_gdxtzbx);
        textview_gdxtzby = view.findViewById(R.id.textview_gdxtzby);
        textview_gdxtzbz = view.findViewById(R.id.textview_gdxtzbz);
        textview_ptzbx = view.findViewById(R.id.textview_ptzbx);
        textview_ptzby = view.findViewById(R.id.textview_ptzby);
        textview_ptzbz = view.findViewById(R.id.textview_ptzbz);
        textview_gddxpc = view.findViewById(R.id.textview_gddxpc);

        viewList.add(textview_gdzt);
        viewList.add(textview_jzzt);
        viewList.add(textview_gdhx);
        viewList.add(textview_gdhy);
        viewList.add(textview_gdzy);
        viewList.add(textview_gdxtzbx);
        viewList.add(textview_gdxtzby);
        viewList.add(textview_gdxtzbz);
        viewList.add(textview_ptzbx);
        viewList.add(textview_ptzby);
        viewList.add(textview_ptzbz);
        viewList.add(textview_gddxpc);
    }

    //初始化模型
    private void initViewModel() {
        // 初始化viewModel
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        //注册观察者
        //订阅viewmodel中的数据变化
        sharedViewModel.getModbusAddress().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String modbusAddress_value) {
                // 在这里处理地址的变化
                modbusAddress = modbusAddress_value;
            }
        });

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

                try {
                    // 只执行一次输入寄存器的读取
                    if (minInputAddress <= maxInputAddress) {
                        inputRegisterValues = modbusMaster.readInputRegisters(slaveAddress, minInputAddress - 30001, maxInputAddress - minInputAddress + 1);
                    }

                    // 只执行一次保持寄存器的读取
                    if (minHoldingAddress <= maxHoldingAddress) {
                        holdingRegisterValues = modbusMaster.readHoldingRegisters(slaveAddress, minHoldingAddress - 40001, maxHoldingAddress - minHoldingAddress + 1);
                    }

                    //遍历viewList,获取每个view的tag对应的是功能名称
                    for (int i = 0; i < viewList.size(); i++) {
                        View view = viewList.get(i);
                        //功能名称存放在view组件的tag中
                        String tag = (String) view.getTag();
                        //通过功能名称在config_Engineering_mode.json中找到对应的配置
                        String jsonData = loadJSONFromAsset("config_Engineering_mode.json"); // 加载 JSON 文件
                        int modbusAddress = Integer.parseInt(getModbusAddressByFunction(jsonData, tag).isEmpty()?"0":getModbusAddressByFunction(jsonData, tag));
                        String bit = getBitByFunction(jsonData, tag);
                        String readWriteType = getReadWriteTypeByFunction(jsonData, tag);
                        String uint = getUintByFunction(jsonData, tag);
                        Integer scaleFactor_int = getScaleFactorByFunction(jsonData, tag).isEmpty()?1:Integer.parseInt(getScaleFactorByFunction(jsonData, tag));
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
                                    // 将读取到的寄存器值进行位操作后显示在TextView中
                                    for (int j = 0; j < quantity; j++) {
                                        //位操作
                                        int bitValue = (registerValues[j] & (1 << Integer.parseInt(bit))) != 0 ? 1 : 0; // 获取当前位

                                        // 在UI线程中更新TextView
                                        mainHandler.post(() ->{
                                            try {
                                                if(view instanceof TextView){
                                                    ((TextView) view ).setText(String.valueOf(bitValue));
                                                }else if(view instanceof EditText){
                                                    ((EditText) view ).setText(String.valueOf(bitValue));
                                                }else if(view instanceof ToggleButton){
                                                    ToggleButton toggleButton = (ToggleButton) view;
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
                                                }
                                            }catch (IllegalStateException e){
                                                Log.e("ThirdfragmentError", "ModbusTCP读取失败,IllegalStateException:"+e.getMessage());
                                                isRunning=false;
                                            }
                                        });
                                    }
                                    break;
                                case "UINT16":
                                    for (int j = 0; j < quantity; j++) {
                                        String stringValue = scaleFactor_float==1.0f?String.valueOf(registerValues[j]):String.valueOf(registerValues[j]/scaleFactor_float);
                                        // 将读取到的寄存器值直接显示在TextView中
                                        mainHandler.post(() ->  {
                                            if(view instanceof TextView){
                                                if (view.getId() == R.id.textview_gdzt)
                                                {
                                                    if(stringValue.equals("0")){
                                                        ((TextView) view ).setText("准备");
                                                    }else if(stringValue.equals("2")){
                                                         ((TextView) view ).setText("对准状态");
                                                    }else if(stringValue.equals("4")){
                                                         ((TextView) view ).setText("对准完成，进入导航状态");
                                                    }else if(stringValue.equals("8")){
                                                         ((TextView) view ).setText("快速的启动失败");
                                                    }else if(stringValue.equals("10")){
                                                         ((TextView) view ).setText("惯导内部故障");
                                                    }else{
                                                        ((TextView) view ).setText(String.valueOf(stringValue));
                                                    }
                                                }else{
                                                    ((TextView) view ).setText(String.valueOf(stringValue));
                                                }
                                            }else if(view instanceof EditText){
                                                if (view.getId() == R.id.textview_gdzt)
                                                {
                                                    if(stringValue.equals("0")){
                                                        ((EditText) view ).setText("准备");
                                                    }else if(stringValue.equals("2")){
                                                        ((EditText) view ).setText("对准状态");
                                                    }else if(stringValue.equals("4")){
                                                        ((EditText) view ).setText("对准完成，进入导航状态");
                                                    }else if(stringValue.equals("8")){
                                                        ((EditText) view ).setText("快速的启动失败");
                                                    }else if(stringValue.equals("10")){
                                                        ((EditText) view ).setText("惯导内部故障");
                                                    }else{
                                                        ((EditText) view ).setText(String.valueOf(stringValue));
                                                    }
                                                }else{
                                                    ((EditText) view ).setText(String.valueOf(stringValue));
                                                }
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
                                        mainHandler.post(() -> {
                                            if(view instanceof TextView){
                                                ((TextView) view ).setText(String.valueOf(stringValue));
                                            }else if(view instanceof EditText){
                                                ((EditText) view ).setText(String.valueOf(stringValue));
                                            }
                                        });
                                    }
                                    break;
                                case "Float":
                                    for (int j = 0; j < quantity; j += 2) {
                                        int low = registerValues[j];//低字节
                                        int high = registerValues[j + 1];//高字节

                                        Float valueViewFloat = registersToFloat(low,high);

                                        // 将读取到的寄存器值直接显示在TextView中
                                        mainHandler.post(() -> {
                                            if(view instanceof TextView){
                                                ((TextView) view ).setText(String.valueOf(valueViewFloat));
                                            }else if(view instanceof EditText){
                                                ((EditText) view ).setText(String.valueOf(valueViewFloat));
                                            }
                                        });
                                    }
                                    break;
                            }
                        }
                    }
                }
                catch (ModbusProtocolException e) {
                    Log.e("ThirdfragmentError", "读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage());
                    // 读取失败后调用回调
                    if (modbusMasterCallback!= null) {
                        modbusMasterCallback.onReadFailure_ModbusProtocolException(e);
                    }
                } catch (ModbusNumberException e) {
                    Log.e("ThirdfragmentError", "读取失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_ModbusNumberException(e);
                    }
                } catch (ModbusIOException e) {
                    Log.e("ThirdfragmentError", "读取失败，读取超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_ModbusIOException(e);
                    }
                }catch (IllegalStateException illegalStateException){
                    Log.e("ThirdfragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                    isRunning=false;
                }catch (Exception e){
                    Log.e("ThirdfragmentError", "读取失败，详细信息：Exception:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_Exception(e);
                    }
                }
                finally {
                    // 始终调度下一个读取
                    if (isRunning) {
                        if (threadHandler!= null) {
                            threadHandler.postDelayed(this, 1000); // 每隔1000ms（1秒）再次执行
                        }
                    } else {
                        if (handlerThread != null) {
                            handlerThread.quitSafely(); // 安全退出线程
                        }
                    }
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

    private Engineering_mode_Activity.ModbusMasterCallback modbusMasterCallback = new Engineering_mode_Activity.ModbusMasterCallback() {
        @Override
        public void onConnectSuccess(ModbusMaster master) {

        }
        @Override
        public void onConnectModbusIOException(Exception e) {

        }
        @Override
        public void onConnectRuntimeException(Exception e) {

        }
        @Override
        public void onConnectException(Exception e) {

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
                    logError(requireContext(),"读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel中的数据
                        sharedViewModel.setIsRunning(isRunning);

                        sharedViewModel.setButtonStartReadEnabled(true);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));
                        sharedViewModel.setButtonStopReadEnabled(false);
                        sharedViewModel.setButtonStopReadBackgroundColor(getResources().getColor(R.color.gray));
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
                    logError(requireContext(),"读取失败，数值异常，异常信息："+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel中的数据
                        sharedViewModel.setIsRunning(isRunning);

                        sharedViewModel.setButtonStartReadEnabled(true);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));
                        sharedViewModel.setButtonStartReadEnabled(false);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.gray));
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
                    logError(requireContext(),"读取失败，读取超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());

                    readFaliedCount++;
                    if(readFaliedCount>=3){
                        isRunning=false;
                        //更新viewmodel中的数据
                        sharedViewModel.setIsRunning(isRunning);

                        sharedViewModel.setButtonStartReadEnabled(true);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));
                        sharedViewModel.setButtonStartReadEnabled(false);
                        sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.gray));
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
                        logError(requireContext(),"读取失败，详细信息：Exception:"+e.getMessage());

                        readFaliedCount++;
                        if(readFaliedCount>=3){
                            isRunning=false;
                            //更新viewmodel中的数据
                            sharedViewModel.setIsRunning(isRunning);

                            sharedViewModel.setButtonStartReadEnabled(true);
                            sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.blue0075F6));
                            sharedViewModel.setButtonStartReadEnabled(false);
                            sharedViewModel.setButtonStartReadBackgroundColor(getResources().getColor(R.color.gray));
                        }
                    }
                });
            }catch (IllegalStateException illegalStateException){
                Log.e("ThirdfragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                isRunning=false;
            }
        }

        @Override
        public void onWriteFailure_ModbusProtocolException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，协议异常，详细信息："+e.getMessage(),Toast.LENGTH_LONG).show();
                    logError(requireContext(),"写入失败，协议异常，详细信息："+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_ModbusNumberException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    logError(requireContext(),"写入失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_ModbusIOException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，写入超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    logError(requireContext(),"写入失败，写入超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_NumberFormatException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，数字格式异常，详细信息：NumberFormatException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    logError(requireContext(),"写入失败，数字格式异常，详细信息：NumberFormatException:"+e.getMessage());
                }
            });
        }
        @Override
        public void onWriteFailure_IllegalArgumentException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，参数无效，详细信息：IllegalArgumentException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    logError(requireContext(),"写入失败，参数无效，详细信息：IllegalArgumentException:"+e.getMessage());
                }
            });
        }
        @Override
        public Void onWriteFailure_TimeoutException(Exception e) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(requireContext(), "写入失败，不符合预期的操作，详细信息：RuntimeException:"+e.getMessage(),Toast.LENGTH_LONG).show();
                    logError(requireContext(),"写入失败，不符合预期的操作，详细信息：RuntimeException:"+e.getMessage());
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
                    logError(requireContext(),"写入失败，详细信息：Exception:"+e.getMessage());
                }
            });
        }
    };


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

    private String loadJSONFromAsset(String fileName) {
        String json = null;
        try {
            InputStream is = getActivity().getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e("ThirdfragmentError", "loadJSONFromAsset失败，Exception:"+e.getMessage());
            Toast.makeText(requireContext(), "loadJSONFromAsset失败，Exception:", Toast.LENGTH_SHORT).show();
        }
        return json;
    }

    public static String getModbusAddressByFunction(String jsonData, String function) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray dataArray = jsonObject.getJSONArray("数据");

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                if (item.getString("功能").equals(function)) {
                    return item.getString("MODBUS地址");
                }
            }
        }
        catch (Exception e) {
            //LogUtils.logError(SecondFragment.this,"读取失败，详细信息：Exception:"+e.getMessage());
        }
        return ""; // 如果没有找到返回 空字符串
    }

    public static String getBitByFunction(String jsonData, String function) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray dataArray = jsonObject.getJSONArray("数据");

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                if (item.getString("功能").equals(function)) {
                    return item.getString("位");
                }
            }
        }
        catch (Exception e) {

        }
        return ""; // 如果没有找到返回 null
    }

    public static String getReadWriteTypeByFunction(String jsonData, String function) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray dataArray = jsonObject.getJSONArray("数据");

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                if (item.getString("功能").equals(function)) {
                    return item.getString("读写类型");
                }
            }
        }
        catch (Exception e) {

        }
        return ""; // 如果没有找到返回 null
    }

    public static String getUintByFunction(String jsonData, String function) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray dataArray = jsonObject.getJSONArray("数据");

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                if (item.getString("功能").equals(function)) {
                    return item.getString("单位");
                }
            }
        }
        catch (Exception e) {

        }
        return ""; // 如果没有找到返回 null
    }

    public static String getScaleFactorByFunction(String jsonData, String function) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray dataArray = jsonObject.getJSONArray("数据");

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                if (item.getString("功能").equals(function)) {
                    return item.getString("缩放因子");
                }
            }
        }
        catch (Exception e) {

        }
        return ""; // 如果没有找到返回 null
    }
}
