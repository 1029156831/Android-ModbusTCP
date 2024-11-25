package com.example.modbustcp_jlibmodbus.fragment;

import static com.example.modbustcp_jlibmodbus.utils.LogUtils.logError;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.example.modbustcp_jlibmodbus.R;
import com.example.modbustcp_jlibmodbus.activity.Engineering_mode_Activity;
import com.example.modbustcp_jlibmodbus.utils.LogUtils;
import com.example.modbustcp_jlibmodbus.utils.ModbusUtils;
import com.example.modbustcp_jlibmodbus.viewmodel.SharedViewModel;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SecondFragment extends Fragment {

    //共享偏好实例
    //存储键值对形式的数据，SharedPreferences可以保存数据，下次打开软件可以读取数据
    //应用可以在不同的生命周期中持久化数据，确保用户的部分设置在应用关闭或重新启动后依然有效
    SharedPreferences preferences;

    //JSON全局配置
    //数据格式顺序（字节转换顺序），默认为CDAB，big-endian值为ABCD，little-endian值为DCBA，big-endian-swap为BADC，little-endian-swap为CDAB，
    static String data_format_order ="CDAB";

    ModbusUtils modbusUtils = new ModbusUtils();

    //XML field页面控件定义

    TextView textview_hzcgqysz,textview_hzcgqmmz,textview_sjcgqysz,textview_sjcgqmmz,textview_sscgqysz,textview_sscgqmmz,textView_xlll,textView_yyll,textView_zldyl,textView_yldyl;
    EditText editText_hzcgqzxz,editText_hzcgqzdz,editText_hzcgqzdxc,editText_sjcgqzxz,editText_sjcgqzdz,editText_sjcgqzdxc,editText_sscgqzxz,
            editText_sscgqzdz,editText_sscgqzdxc,editText_hzjzz,editText_sjjzz,editText_ssjzz,editText_zdjzwzqr,editText_xlll,editText_yyll,editText_zldyl,
            editText_yldyl,editText_zdjzsd,editText_zzjgsjsd,editText_zzjghzsd,editText_zzjgsssd,editText_jyjgsjsd,editText_jyjghzsd,editText_jyjgsssd;

    Button button_hzcgqzxz,button_hzcgqzdz,button_hzcgqzdxc,button_sjcgqzxz,button_sjcgqzdz,button_sjcgqzdxc,button_sscgqzxz,
            button_sscgqzdz,button_sscgqzdxc,button_hzjzz,button_sjjzz,button_ssjzz,
            button_zdjzsd,button_zzjgsjsd,button_zzjghzsd,button_zzjgsssd,button_jyjgsjsd, button_jyjghzsd,button_jyjgsssd;
    ToggleButton toggleButton_zdjzwzqr;

    LinearLayout linearLayout_second;
    ScrollView scrollView_second;

    //Modbus
    ModbusMaster modbusMaster;
    String modbusAddress;
    //IP地址
    private String ipAddress="192.168.1.10";
    //端口
    private int port=503;
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

    // 存储输入寄存器和保持寄存器的地址,因页面是固定布局和对应固定modbus地址，所以这里直接写死
    int minInputAddress = 30001;
    int maxInputAddress = 30029;
    int minHoldingAddress = 40001;
    int maxHoldingAddress = 40038;

    // 创建一个共享视图模型
    SharedViewModel sharedViewModel ;
    int secondFragmentTabLayoutHeight;

    //创建一个可以遍历的集合
    private List<View> viewList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_second, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViewModel();

        initComponent(view);

        linearLayout_second = view.findViewById(R.id.linearLayout_second);
        linearLayout_second.post(new Runnable() {
            @Override
            public void run() {
                int totalHeight = linearLayout_second.getHeight();
                sharedViewModel.setSecondFragmentTabLayoutHeight(totalHeight);
                // 这里可以使用 totalHeight 进行下一步操作
            }
        });

        linearLayout_second.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int totalHeight = linearLayout_second.getHeight();
                sharedViewModel.setSecondFragmentTabLayoutHeight(totalHeight);
                linearLayout_second.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
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

    //初始化组件
    private void initComponent(View view) {
        //将需要遍历赋值的view存入集合
        viewList = new ArrayList<>();
        textview_hzcgqysz = view.findViewById(R.id.textview_hzcgqysz);
        textview_hzcgqmmz = view.findViewById(R.id.textview_hzcgqmmz);
        textview_sjcgqysz = view.findViewById(R.id.textview_sjcgqysz);
        textview_sjcgqmmz = view.findViewById(R.id.textview_sjcgqmmz);
        textview_sscgqysz = view.findViewById(R.id.textview_sscgqysz);
        textview_sscgqmmz = view.findViewById(R.id.textview_sscgqmmz);
        textView_xlll = view.findViewById(R.id.textView_xlll);
        textView_yyll = view.findViewById(R.id.textView_yyll);
        textView_zldyl = view.findViewById(R.id.textView_zldyl);
        textView_yldyl = view.findViewById(R.id.textView_yldyl);
        editText_hzcgqzxz = view.findViewById(R.id.editText_hzcgqzxz);
        editText_hzcgqzdz = view.findViewById(R.id.editText_hzcgqzdz);
        editText_hzcgqzdxc = view.findViewById(R.id.editText_hzcgqzdxc);
        editText_sjcgqzxz = view.findViewById(R.id.editText_sjcgqzxz);
        editText_sjcgqzdz = view.findViewById(R.id.editText_sjcgqzdz);
        editText_sjcgqzdxc = view.findViewById(R.id.editText_sjcgqzdxc);
        editText_sscgqzxz = view.findViewById(R.id.editText_sscgqzxz);
        editText_sscgqzdz = view.findViewById(R.id.editText_sscgqzdz);
        editText_sscgqzdxc = view.findViewById(R.id.editText_sscgqzdxc);
        editText_hzjzz = view.findViewById(R.id.editText_hzjzz);
        editText_sjjzz = view.findViewById(R.id.editText_sjjzz);
        editText_ssjzz = view.findViewById(R.id.editText_ssjzz);
        editText_zdjzwzqr = view.findViewById(R.id.editText_zdjzwzqr);
        editText_zdjzsd = view.findViewById(R.id.editText_zdjzsd);
        editText_zzjgsjsd = view.findViewById(R.id.editText_zzjgsjsd);
        editText_zzjghzsd = view.findViewById(R.id.editText_zzjghzsd);
        editText_zzjgsssd = view.findViewById(R.id.editText_zzjgsssd);
        editText_jyjgsjsd = view.findViewById(R.id.editText_jyjgsjsd);
        editText_jyjghzsd = view.findViewById(R.id.editText_jyjghzsd);
        editText_jyjgsssd = view.findViewById(R.id.editText_jyjgsssd);

        viewList.add(textview_hzcgqysz);
        viewList.add(textview_hzcgqmmz);
        viewList.add(textview_sjcgqysz);
        viewList.add(textview_sjcgqmmz);
        viewList.add(textview_sscgqysz);
        viewList.add(textview_sscgqmmz);
        viewList.add(textView_xlll);
        viewList.add(textView_yyll);
        viewList.add(textView_zldyl);
        viewList.add(textView_yldyl);
        viewList.add(editText_hzcgqzxz);
        viewList.add(editText_hzcgqzdz);
        viewList.add(editText_hzcgqzdxc);
        viewList.add(editText_sjcgqzxz);
        viewList.add(editText_sjcgqzdz);
        viewList.add(editText_sjcgqzdxc);
        viewList.add(editText_sscgqzxz);
        viewList.add(editText_sscgqzdz);
        viewList.add(editText_sscgqzdxc);
        viewList.add(editText_hzjzz);
        viewList.add(editText_sjjzz);
        viewList.add(editText_ssjzz);
        viewList.add(editText_zdjzwzqr);
        viewList.add(editText_zdjzsd);
        viewList.add(editText_zzjgsjsd);
        viewList.add(editText_zzjghzsd);
        viewList.add(editText_zzjgsssd);
        viewList.add(editText_jyjgsjsd);
        viewList.add(editText_jyjghzsd);
        viewList.add(editText_jyjgsssd);

        button_hzcgqzxz = view.findViewById(R.id.button_hzcgqzxz);
        button_hzcgqzdz = view.findViewById(R.id.button_hzcgqzdz);
        button_hzcgqzdxc = view.findViewById(R.id.button_hzcgqzdxc);
        button_sjcgqzxz = view.findViewById(R.id.button_sjcgqzxz);
        button_sjcgqzdz = view.findViewById(R.id.button_sjcgqzdz);
        button_sjcgqzdxc = view.findViewById(R.id.button_sjcgqzdxc);
        button_sscgqzxz = view.findViewById(R.id.button_sscgqzxz);
        button_sscgqzdz = view.findViewById(R.id.button_sscgqzdz);
        button_sscgqzdxc = view.findViewById(R.id.button_sscgqzdxc);
        button_hzjzz = view.findViewById(R.id.button_hzjzz);
        button_sjjzz = view.findViewById(R.id.button_sjjzz);
        button_ssjzz = view.findViewById(R.id.button_ssjzz);
        button_zdjzsd = view.findViewById(R.id.button_zdjzsd);
        button_zzjgsjsd = view.findViewById(R.id.button_zzjgsjsd);
        button_zzjghzsd = view.findViewById(R.id.button_zzjghzsd);
        button_zzjgsssd = view.findViewById(R.id.button_zzjgsssd);
        button_jyjgsjsd = view.findViewById(R.id.button_jyjgsjsd);
        button_jyjghzsd = view.findViewById(R.id.button_jyjghzsd);
        button_jyjgsssd = view.findViewById(R.id.button_jyjgsssd);

        final View.OnClickListener buttonCLickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 清除所有编辑组件的焦点
                clearAllEditTextFocus();

                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                builder.setTitle("编辑值");

                // 创建一个布局用于输入
                LinearLayout layout = new LinearLayout(requireContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                //通过功能名称
                String tag = v.getTag().toString();
                //根据tag找到对应的编辑组件
                EditText editText = requireView().findViewWithTag(tag);

                // 从tableRow获取当前值
                String currentValue = editText.getText().toString();
                EditText valueInput = new EditText(requireContext());
                // 设置输入类型为数字或小数
                //valueInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL );
                valueInput.setText(currentValue); // 设置当前值
                valueInput.setFilters(new InputFilter[] { createNumberInputFilter() }); // 设置过滤器
                layout.addView(valueInput);
                builder.setView(layout);
                //通过功能名称在config_Engineering_mode.json中找到对应的配置
                String jsonData = loadJSONFromAsset("config_Engineering_mode.json"); // 加载 JSON 文件
                String modbusAddress = getModbusAddressByFunction(jsonData, tag).isEmpty()?"0":getModbusAddressByFunction(jsonData, tag);
                String bit = getBitByFunction(jsonData, tag);
                String readWriteType = getReadWriteTypeByFunction(jsonData, tag);
                String unit = getUintByFunction(jsonData, tag);
                String scaleFactor_str = getScaleFactorByFunction(jsonData, tag);
                //缩放因子默认为1，如果为空则设置为1
                Integer scaleFactor_int = scaleFactor_str.isEmpty()?1:Integer.parseInt(scaleFactor_str);
                Float scaleFactor_float = scaleFactor_int.floatValue();

                builder.setPositiveButton("确认", (dialog, which) -> {
                    String newValue = valueInput.getText().toString();
                    editText.setText(newValue);

                    // 使用 RxJava 处理数据发送
                    handleValueWrite(modbusAddress, slaveAddress, unit, newValue,scaleFactor_float);
                });

                builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
                builder.show();
            }
        };

        button_hzcgqzxz.setOnClickListener(buttonCLickListener);
        button_hzcgqzdz.setOnClickListener(buttonCLickListener);
        button_hzcgqzdxc.setOnClickListener(buttonCLickListener);
        button_sjcgqzxz.setOnClickListener(buttonCLickListener);
        button_sjcgqzdz.setOnClickListener(buttonCLickListener);
        button_sjcgqzdxc.setOnClickListener(buttonCLickListener);
        button_sscgqzxz.setOnClickListener(buttonCLickListener);
        button_sscgqzdz.setOnClickListener(buttonCLickListener);
        button_sscgqzdxc.setOnClickListener(buttonCLickListener);
        button_hzjzz.setOnClickListener(buttonCLickListener);
        button_sjjzz.setOnClickListener(buttonCLickListener);
        button_ssjzz.setOnClickListener(buttonCLickListener);
        button_zdjzsd.setOnClickListener(buttonCLickListener);
        button_zzjgsjsd.setOnClickListener(buttonCLickListener);
        button_zzjghzsd.setOnClickListener(buttonCLickListener);
        button_zzjgsssd.setOnClickListener(buttonCLickListener);
        button_jyjgsjsd.setOnClickListener(buttonCLickListener);
        button_jyjghzsd.setOnClickListener(buttonCLickListener);
        button_jyjgsssd.setOnClickListener(buttonCLickListener);

        toggleButton_zdjzwzqr = view.findViewById(R.id.toggleButton_zdjzwzqr);

        setCommonTouchListener(toggleButton_zdjzwzqr);

        scrollView_second = view.findViewById(R.id.scrollView_second);
        linearLayout_second = view.findViewById(R.id.linearLayout_second);
    }

    //触发式，自恢复
    private void setCommonTouchListener(View button) {
        button.setOnTouchListener((v, event) -> {
            //功能名称存放在view组件的tag中
            String tag = (String) v.getTag();
            //通过功能名称在config_Engineering_mode.json中找到对应的配置
            String jsonData = loadJSONFromAsset("config_Engineering_mode.json"); // 加载 JSON 文件
            int modbusAddress = Integer.parseInt(getModbusAddressByFunction(jsonData, tag).isEmpty()?"0":getModbusAddressByFunction(jsonData, tag));
            String bit = getBitByFunction(jsonData, tag);
            String readWriteType = getReadWriteTypeByFunction(jsonData, tag);
            String uint = getUintByFunction(jsonData, tag);
            Integer scaleFactor_int = getScaleFactorByFunction(jsonData, tag).isEmpty()?1:Integer.parseInt(getScaleFactorByFunction(jsonData, tag));
            Float scaleFactor_float = scaleFactor_int.floatValue();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 长按时显示绿色
                    toggleButton_zdjzwzqr.setText("ON");
                    // 设置背景色调
                    ColorStateList colorStateList_green = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green)); // 使用红色作为示例
                    toggleButton_zdjzwzqr.setBackgroundTintList(colorStateList_green);

                    Thread down = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //发送数据到保持寄存器
                            // 判断newValue是否是float类型
                            try {
                                int address=modbusAddress-40001;

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
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                // 写入失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                }
                            } catch (ModbusProtocolException e) {
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                // 写入失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                }
                            } catch (ModbusNumberException e) {
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                }
                            } catch (ModbusIOException e) {
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                }
                            }catch (IllegalStateException illegalStateException){
                                Log.e("SecondFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                            }catch (Exception e){
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
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
                    toggleButton_zdjzwzqr.setText("OFF");
                    // 设置背景色调
                    ColorStateList colorStateList_gray = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray)); // 使用红色作为示例
                    toggleButton_zdjzwzqr.setBackgroundTintList(colorStateList_gray);

                    Thread cancel = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            //发送数据到保持寄存器
                            // 判断newValue是否是float类型
                            try {
                                int address=modbusAddress-40001;

                                // 读取当前值
                                int currentValue = modbusMaster.readHoldingRegisters(slaveAddress, address, 1)[0];

                                // 设置当前位为0
                                int bitPosition = Integer.parseInt(bit); // 当前位的位置
                                int newValue = currentValue & ~(1 << bitPosition); // 清除当前位（设置为0）
                                // 写入更改后的值
                                modbusMaster.writeSingleRegister(slaveAddress, address, newValue);
                            } catch (NumberFormatException e) {
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,NumberFormatException:"+e.getMessage());
                                // 写入失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_NumberFormatException(e);
                                }
                            } catch (ModbusProtocolException e) {
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusProtocolException:"+e.getMessage());
                                // 写入失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                }
                            } catch (ModbusNumberException e) {
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusNumberException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                }
                            } catch (ModbusIOException e) {
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusIOException:"+e.getMessage());
                                if(modbusMasterCallback!= null){
                                    modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                }
                            }catch (IllegalStateException illegalStateException){
                                Log.e("SecondFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                            }catch (Exception e){
                                Log.e("SecondFragmentError", "ModbusTCP写入失败,ModbusException:"+e.getMessage());
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
    };

    // 辅助方法：处理值的写入
    private void handleValueWrite(String modbusAddress, Integer slaveAddress, String unit, String newValue,Float scaleFactor_float) {
        Single.fromCallable(() -> {
                    int address = Integer.parseInt(modbusAddress) - 40001;

                    if (unit.equals("Float")) {
                        float floatValue = scaleFactor_float==1.0f?Float.parseFloat(newValue):Float.parseFloat(newValue)*scaleFactor_float;
                        int[] floatToRegister = modbusUtils.floatToRegisters(floatValue);
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
                    Log.e("SecondFragmentError", "ModbusTCP写入失败: " + throwable.getMessage());
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

    // 定义一个方法来清除所有 EditText 的焦点
    private void clearAllEditTextFocus() {
        editText_hzcgqzxz.clearFocus();
        editText_hzcgqzdz.clearFocus();
        editText_hzcgqzdxc.clearFocus();
        editText_sjcgqzxz.clearFocus();
        editText_sjcgqzdz.clearFocus();
        editText_sjcgqzdxc.clearFocus();
        editText_sscgqzxz.clearFocus();
        editText_sscgqzdz.clearFocus();
        editText_sscgqzdxc.clearFocus();
        editText_hzjzz.clearFocus();
        editText_sjjzz.clearFocus();
        editText_ssjzz.clearFocus();
        editText_zdjzwzqr.clearFocus();
        editText_zdjzsd.clearFocus();
        editText_zzjgsjsd.clearFocus();
        editText_zzjghzsd.clearFocus();
        editText_zzjgsssd.clearFocus();
        editText_jyjgsjsd.clearFocus();
        editText_jyjghzsd.clearFocus();
        editText_jyjgsssd.clearFocus();
    }

    @Override
    public void onStop() {
        super.onStop();
        if(threadHandler!= null) {
            threadHandler.removeCallbacksAndMessages(null); // 移除所有未处理的消息
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
                    logError(requireContext(),"读取失败，数值异常，异常信息："+e.getMessage());

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
            try {
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
                Log.e("SecondFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                isRunning=false;
            }
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
                Log.e("SecondFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
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
                                                Log.e("SecondFragmentError", "ModbusTCP读取失败,IllegalStateException:"+e.getMessage());
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
                                                ((TextView) view ).setText(String.valueOf(stringValue));
                                            }else if(view instanceof EditText){
                                                ((EditText) view ).setText(String.valueOf(stringValue));
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
                    Log.e("SecondFragmentError", "读取失败，协议异常，详细信息：ModbusProtocolException:"+e.getMessage());
                    // 读取失败后调用回调
                    if (modbusMasterCallback!= null) {
                        modbusMasterCallback.onReadFailure_ModbusProtocolException(e);
                    }
                } catch (ModbusNumberException e) {
                    Log.e("SecondFragmentError", "读取失败，数值异常，详细信息：ModbusNumberException:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_ModbusNumberException(e);
                    }
                } catch (ModbusIOException e) {
                    Log.e("SecondFragmentError_secondFragment", "读取失败，读取超时请检查设备及网络状况，详细信息：ModbusIOException:"+e.getMessage());
                    if(modbusMasterCallback!= null){
                        modbusMasterCallback.onReadFailure_ModbusIOException(e);
                    }
                }catch (IllegalStateException illegalStateException){
                    Log.e("SecondFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
                    isRunning=false;
                }catch (Exception e){
                    Log.e("SecondFragmentError", "读取失败，详细信息：Exception:"+e.getMessage());
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

    private String loadJSONFromAsset(String fileName) {
        String json = null;
        try {
            InputStream is = requireActivity().getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IllegalStateException illegalStateException){
            Log.e("SecondFragmentError", "ModbusTCP读取失败,IllegalStateException:"+illegalStateException.getMessage());
        }catch (Exception e) {
            Log.e("SecondFragmentError", "loadJSONFromAsset失败，Exception:"+e.getMessage());
            Toast.makeText(requireContext(), "loadJSONFromAsset失败，Exception:", Toast.LENGTH_SHORT).show();
        }
        return json;
    }
}
