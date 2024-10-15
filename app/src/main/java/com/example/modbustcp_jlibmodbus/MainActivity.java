package com.example.modbustcp_jlibmodbus;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.util.Consumer;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusProtocolException;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.net.InetAddress;
import java.util.Objects;

import android.Manifest;

/** @noinspection deprecation*/
public class MainActivity extends AppCompatActivity {

    //XML field页面控件定义
    EditText input_IP, input_port,input_slave_address,input_offset,input_bit, input_decimal;
    Button button_connect,button_disconnect,button_readMB,button_writeMB,button_select_file;
    //RadioButton radioButton_readMB,radioButton_writeMB;
    Spinner spinner_dataType;
    ArrayAdapter<String> spinnerAdapter_dataType;
    TextView textView_result;
    ConnectivityManager conMgr;
    //XML field

    //主线程
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    //Modbus
    ModbusMaster master;
    TcpParameters tcpParameters;
    InetAddress adress;
    //IP地址
    private String ip="172.20.10.5";
    //端口
    private int port=502;
    //从站地址
    private int slaveAddress=1;
    //寄存器读取开始地址
    private int offset=0;
    //读取的寄存器数量
    private int quantity = 1;
    private String data="";
    //开启定时自动刷新标志位
    private boolean refresh_on = false;
    private int refreshDelay = 500;
    private int selectedDataType = 0;
    //数据类型选择
    private static final String[] DATA_TYPE_SELECTIONS = {"0x01读线圈状态","0x02读输入状态","0x03读保持寄存器","0x04读输入寄存器","0x05写单个线圈","0x06写单个保持寄存器","0x0F写多个线圈","0x10写多个保持寄存器"};
    //请求时间
    private static final String[] POSTTIME_SELECTIONS = {"100 ms","200 ms","500 ms","1 second","2 seconds","5 seconds","10 seconds"};
    //默认IP，使用调试时电脑IP地址方便调试
    private static final String DEFAULT_IP = "192.168.11.87";
    private static final int Default_Port = 502,Default_SlaveAddress = 1;
    private String textView_result_Content = "";
    //请求权限标志位
    private static final int PERMISSION_REQUEST_CODE_Wifi = 1;
    private static final int PERMISSION_REQUEST_CODE_File = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //find view and get the listeners设置组件监听器
        initial();

        //存储键值对形式的数据，SharedPreferences可以保存数据，下次打开软件可以读取数据
        //应用可以在不同的生命周期中持久化数据，确保用户的部分设置在应用关闭或重新启动后依然有效
        SharedPreferences preferences = getSharedPreferences("Preference",0);
        //读取用户之前保存的IP地址。如果该地址未被存储，则返回空字符串
        String pref_IP = preferences.getString("ip","");
        //如果IP地址为空，则使用默认IP地址
        if(pref_IP.isEmpty()){
            ip = DEFAULT_IP;
        }else{//否则使用之前保存的IP地址
            ip = pref_IP;
            input_IP.setText(pref_IP);
        }
        //读取用户之前保存的Port。如果该未被存储，则返回空字符串
        String pref_port = preferences.getString("port","");
        if(pref_port.isEmpty()){
            port = Default_Port;
        }else {
            port = Integer.parseInt(pref_port);
            input_port.setText(pref_port);
        }

        String pref_slaveaddress = preferences.getString("slave_address","");
        if(pref_slaveaddress.isEmpty()){
            slaveAddress = Default_SlaveAddress;
        }else {
            slaveAddress = Integer.parseInt(pref_slaveaddress);
            input_port.setText(pref_slaveaddress);
        }

    }

    private void initial() {
        input_IP = findViewById(R.id.input_IP);
        input_port = findViewById(R.id.input_port);
        //input_slave_address = (EditText)findViewById(R.id.input_slave_address);
        input_offset = findViewById(R.id.input_offset);
        input_bit = findViewById(R.id.input_bit);
        input_decimal = findViewById(R.id.input_decimal);
        button_connect = findViewById(R.id.button_connect);
        button_disconnect = findViewById(R.id.button_disconnect);
        button_select_file = findViewById(R.id.button_select_file);
        button_readMB = findViewById(R.id.button_readMB);
        button_writeMB = findViewById(R.id.button_writeMB);
        //radioButton_readMB = findViewById(R.id.radio_readMB);
        //radioButton_writeMB = findViewById(R.id.radio_writeMB);
        spinner_dataType = findViewById(R.id.spinner_dataType);
        textView_result = findViewById(R.id.text_result);

        button_connect.setEnabled(true);
        button_disconnect.setEnabled(false);
        button_readMB.setEnabled(false);
        button_writeMB.setEnabled(false);

        //这个适配器的作用是将DATA_TYPE_SELECTIONS中的数据与Spinner组件进行绑定，使得用户可以通过下拉列表选择其中的选项
        spinnerAdapter_dataType = new ArrayAdapter<>(MainActivity.this,android.R.layout.select_dialog_item,DATA_TYPE_SELECTIONS);
        spinner_dataType.setAdapter(spinnerAdapter_dataType);
        //id为 spinner_dataType 的 Spinner组件设置一个监听器，用于监听当用户选择了下拉列表中的一个选项时的事件
        spinner_dataType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDataType = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        conMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        // 自定义一个通用的 TextWatcher
        class CustomTextWatcher implements TextWatcher {
            private final Consumer<Integer> onTextChanged;

            public CustomTextWatcher(Consumer<Integer> onTextChanged) {
                this.onTextChanged = onTextChanged;
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 在文本改变之前的操作
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                // 当文本正在改变时
            }

            @Override
            public void afterTextChanged(Editable s) {
                int value = s.toString().isEmpty() ? 0 : Integer.parseInt(s.toString());
                onTextChanged.accept(value); // 使用接收者处理文本改变
            }
        }

        // 创建一个接受整型的函数式接口
        @FunctionalInterface
        interface Consumer<T> {
            void accept(T t);
        }

        // 使用通用的 TextWatcher
        input_offset.addTextChangedListener(new CustomTextWatcher(value -> {
            offset = value; // 处理 offset 赋值
        }));

        input_bit.addTextChangedListener(new CustomTextWatcher(value -> {
            quantity = value; // 处理 quantity 赋值
        }));

        input_decimal.addTextChangedListener(new CustomTextWatcher(value -> {
            data = value.toString(); // 处理 data 赋值
        }));


        final View.OnClickListener buttonCLickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //连接按钮
                if (v.getId() == R.id.button_connect) {
                    // 动态申请权限
                    checkPermissions();
                    //检查是否连接WIFI
                    checkWifiConnection();

                    Thread init = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // 设置主机TCP参数
                                tcpParameters = new TcpParameters();
                                // 设置TCP的ip地址
                                adress = InetAddress.getByName(input_IP.getText().toString());
                                // TCP参数设置ip地址
                                // tcpParameters.setHost(InetAddress.getLocalHost());
                                tcpParameters.setHost(adress);
                                // TCP设置长连接
                                tcpParameters.setKeepAlive(true);
                                // TCP设置端口，这里设置是默认端口502
                                tcpParameters.setPort(input_port.getText().toString().isEmpty() ? Default_Port : Integer.parseInt(input_port.getText().toString()));
                                // 创建一个主机
                                master = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
                                //设置递增的事务ID
                                Modbus.setAutoIncrementTransactionId(true);
                                if (!master.isConnected()) {
                                    master.connect();// 开启连接
                                }


                                Log.d("ModbusTCP", "ModbusTCP连接成功");
                                // 连接成功后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onConnectSuccess(master);
                                }
                            } catch (ModbusIOException e) {
                                Log.e("ModbusTCP", "ModbusTCP连接失败,ModbusIOException:"+e.getMessage());
                                // 连接失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onConnectFailure(e);
                                }
                            } catch (RuntimeException e) {
                                Log.e("ModbusTCP", "ModbusTCP连接失败,RuntimeException:"+e.getMessage());
                                // 连接失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onConnectFailure(e);
                                }
                                throw e;
                            } catch (Exception e) {
                                Log.e("ModbusTCP", "ModbusTCP连接失败,Exception:"+e.getMessage());
                                // 连接失败后调用回调
                                if (modbusMasterCallback!= null) {
                                    modbusMasterCallback.onConnectFailure(e);
                                }
                            }
                        }
                    });
                    init.start();
                }
                //断开按钮
                else if(v.getId() == R.id.button_disconnect) {
                    try {
                        master.disconnect();
                        Toast.makeText(MainActivity.this,"已断开",Toast.LENGTH_SHORT).show();
                    } catch (ModbusIOException e) {
                        Log.e("ModbusTCPError", "ModbusTCP断开失败,ModbusIOException:"+e.getMessage());
                        Toast.makeText(MainActivity.this,"断开失败",Toast.LENGTH_SHORT).show();
                    }catch (Exception e) {
                        Log.e("ModbusTCPError", "ModbusTCP断开失败,Exception:"+e.getMessage());
                        Toast.makeText(MainActivity.this,"断开失败",Toast.LENGTH_SHORT).show();
                    }
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    button_readMB.setEnabled(false);
                    button_writeMB.setEnabled(false);
                }
                //读取按钮
                else if(v.getId() == R.id.button_readMB) {
                    //初始化内容
                    textView_result_Content = "";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            textView_result.setText("");
                        }
                    });

                    Thread init = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (!master.isConnected()){
                                Toast.makeText(MainActivity.this,"请先连接ModbusTCP",Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Log.d("selectedDataType","当前选择列:"+selectedDataType);

                            if(selectedDataType == 0){//读取线圈状态
                                try {
                                    // 读取对应从机的数据，readCoils读取的线圈状态，功能码01
                                    boolean[] registerValues = master.readCoils(slaveAddress, offset, quantity);
                                    if (quantity > 0 && registerValues.length>0) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                for (int i = 0; i < quantity; i++){
                                                    if (i==0)textView_result.append(String.valueOf(registerValues[i]));
                                                    else textView_result.append(","+String.valueOf(registerValues[i]));
                                                }
                                            }
                                        });
                                    }
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取线圈状态失败,ModbusProtocolException:"+e.getMessage());
                                    // 读取失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onReadFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取线圈状态失败,ModbusNumberException:"+e.getMessage());
                                    // 读取失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onReadFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取线圈状态失败,ModbusIOException:"+e.getMessage());
                                    // 读取失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onReadFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else if(selectedDataType == 1){//读取离散输入状态
                                try {
                                    // 读取对应从机的数据，readDiscreteInputs读取离散输入状态，功能码02
                                    boolean[] registerValues = master.readDiscreteInputs(slaveAddress, offset, quantity);
                                    if (quantity > 0 && registerValues.length>0) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                for (int i = 0; i < quantity; i++){
                                                    if (i==0)textView_result.append(String.valueOf(registerValues[i]));
                                                    else textView_result.append(","+String.valueOf(registerValues[i]));
                                                }
                                            }
                                        });
                                    }
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取离散输入失败,ModbusProtocolException:"+e.getMessage());
                                    // 读取失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onReadFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取离散输入失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onReadFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取离散输入失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onReadFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else if(selectedDataType == 2){//读取保持寄存器
                                try {
                                    // 读取对应从机的数据，readHoldingRegisters读取的保持寄存器，功能码03
                                    int[] registerValues = master.readHoldingRegisters(slaveAddress, offset, quantity);
                                    if (quantity > 0 && registerValues.length>0) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                // 将读取到的寄存器值显示在TextView中
                                                for (int i = 0; i < quantity; i++){
                                                    if (i==0)textView_result.append("寄存器数据："+String.valueOf(registerValues[i]));
                                                    else textView_result.append(","+String.valueOf(registerValues[i]));
                                                }

                                                // 将读取到的寄存器值进行位操作后显示在TextView中
                                                for (int i = 0; i < quantity; i++){
                                                    StringBuilder output = new StringBuilder(); // 用于拼接输出的字符串
                                                    // 位操作示例：获取第0位、第1位和第2位的值
                                                    int value = registerValues[i];
                                                    for (int bitIndex = 0; bitIndex < 16; bitIndex++) {
                                                        int bitValue =  (value & (1 << bitIndex)) != 0 ?1:0; // 获取当前位
                                                        output.append("第").append(bitIndex).append("位: ").append(bitValue);
                                                        if (bitIndex < 15) output.append(", "); // 换行处理，除最后一位外在后面加逗号
                                                    }
                                                    output.append(")");

                                                    // 将拼接好的字符串输出到TextView中
                                                    textView_result.append("\r\n位操作后数据："+output.toString());
                                                }
                                            }
                                        });
                                    }

                                }  catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取保持寄存器失败,ModbusProtocolException:"+e.getMessage());
                                    // 读取失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onReadFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取保持寄存器失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onReadFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取保持寄存器失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onReadFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else if(selectedDataType == 3){//读取输入寄存器
                                try {
                                    // 读取对应从机的数据，readInputRegisters读取的写寄存器，功能码04
                                    int[] registerValues = master.readInputRegisters(slaveAddress, offset, quantity);
                                    if (quantity > 0 && registerValues.length>0) {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                // 将读取到的寄存器值显示在TextView中
                                                for (int i = 0; i < quantity; i++){
                                                    if (i==0)textView_result.append(String.valueOf(registerValues[i]));
                                                    else textView_result.append(","+String.valueOf(registerValues[i]));
                                                }
                                            }
                                        });
                                    }
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取输入寄存器失败,ModbusProtocolException:"+e.getMessage());
                                    // 读取失败后调用回调
                                    if (modbusMasterCallback!= null) {
                                        modbusMasterCallback.onReadFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取输入寄存器失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onReadFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP读取输入寄存器失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onReadFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this,"请选择读取相关操作类型",Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    });
                    init.start();
                }
                //写入按钮
                else if(v.getId() == R.id.button_writeMB) {
                    //初始化内容
                    textView_result_Content = "";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            textView_result.setText("");
                        }
                    });

                    Thread init = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (!master.isConnected()){
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this,"请先连接ModbusTCP",Toast.LENGTH_SHORT).show();
                                    }
                                });
                                return;
                            }
                            if(selectedDataType == 4){//写入单个线圈状态
                                try {
                                    boolean writeFlage = false;
                                    if (data.equals("1")){
                                        writeFlage = true;
                                    }else if (data.equals("0")){
                                        writeFlage = false;
                                    }else {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,"注意：写入线圈时输入值只能为0或1，1表示开，0表示关",Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                    // 写入对应从机的数据，writeSingleCoil写入单个线圈状态，功能码05
                                    master.writeSingleCoil(slaveAddress, offset, writeFlage);
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入线圈状态失败,ModbusProtocolException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入线圈状态失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入线圈状态失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!=null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else if(selectedDataType == 5){//写入单个保持寄存器
                                try {
                                    int writeFlage = 0;
                                    if (!data.isEmpty()){
                                        writeFlage = Integer.parseInt(data);
                                    }else {
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,"注意：写入寄存器时输入值不能为空",Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                    // 写入对应从机的数据，writeSingleRegister写入单个保持寄存器，功能码06
                                    master.writeSingleRegister(slaveAddress, offset, writeFlage);
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入寄存器失败,ModbusProtocolException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入寄存器失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入寄存器失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!=null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else if(selectedDataType == 6){//写入多个线圈状态
                                try {
                                    boolean[] writeFlage = new boolean[quantity];
                                    //当输入值不为空时，
                                    if (!data.isEmpty()){
                                        //转二进制
                                        //String binaryStr = Integer.toBinaryString(Integer.parseInt(data));
                                        String str = Integer.toString(Integer.parseInt(data));
                                        //writeFlage = new boolean[binaryStr.length()];
                                        for (int i = 0; i < str.length(); i++) {
                                            writeFlage[i] = str.charAt(i) == '1';
                                        }
                                    }else {
                                        writeFlage = new boolean[quantity];
                                    }
                                    // 写入对应从机的数据，writeMultipleCoils写入多个线圈状态，功能码15，16进制为0F
                                    master.writeMultipleCoils(slaveAddress, offset, writeFlage);
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入多个线圈状态失败,ModbusProtocolException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入多个线圈状态失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入多个线圈状态失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!=null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else if(selectedDataType == 7){//写入多个保持寄存器
                                try {
                                    int writeFlage[] = new int[quantity];
                                    //当输入值不为空时，
                                    if (!data.isEmpty()){
                                        //转二进制
                                        //String binaryStr = Integer.toBinaryString( Integer.parseInt(data));
                                        String str = Integer.toString( Integer.parseInt(data));
                                        //writeFlage = new boolean[binaryStr.length()];
                                        for (int i = 0; i < str.length(); i++) {
                                            writeFlage[i] = str.charAt(i) == '1' ? 1 : 0;
                                        }
                                    }else {
                                        writeFlage = new int[quantity];
                                    }
                                    if (data.isEmpty()){
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,"注意：写入寄存器时输入值不能为空",Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                    // 写入对应从机的数据，writeMultipleRegisters写入多个保持寄存器，功能码16，16进制为10
                                    master.writeMultipleRegisters(slaveAddress, offset, writeFlage);
                                } catch (ModbusProtocolException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入寄存器失败,ModbusProtocolException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusProtocolException(e);
                                    }
                                } catch (ModbusNumberException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入寄存器失败,ModbusNumberException:"+e.getMessage());
                                    if(modbusMasterCallback!= null){
                                        modbusMasterCallback.onWriteFailure_ModbusNumberException(e);
                                    }
                                } catch (ModbusIOException e) {
                                    Log.e("ModbusTCPError", "ModbusTCP写入寄存器失败,ModbusIOException:"+e.getMessage());
                                    if(modbusMasterCallback!=null){
                                        modbusMasterCallback.onWriteFailure_ModbusIOException(e);
                                    }
                                }
                            }
                            else {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this,"请选择写入相关操作类型",Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    });
                    init.start();
                }
                //配置本地参数按钮
                else if(v.getId() == R.id.button_select_file){
                    checkAndRequestPermissions();
                }
            }
        };

        button_connect.setOnClickListener(buttonCLickListener);
        button_disconnect.setOnClickListener(buttonCLickListener);
        button_readMB.setOnClickListener(buttonCLickListener);
        button_writeMB.setOnClickListener(buttonCLickListener);
        button_select_file.setOnClickListener(buttonCLickListener);
    }

    private void checkPermissions() {
        // 检查WiFi状态权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            // 权限未被授予，进行请求
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CHANGE_WIFI_STATE},
                    PERMISSION_REQUEST_CODE_Wifi);
        } else {
            // 权限已被授予，可以执行相关操作
            Toast.makeText(this, "WiFi权限已被授予", Toast.LENGTH_SHORT).show();
            // 这里可以添加需要WiFi权限的操作
        }
    }

    public interface ModbusMasterCallback {
        void onConnectSuccess(ModbusMaster master);
        void onConnectFailure(Exception e);

        void onReadSuccess();
        void onReadFailure_ModbusProtocolException(Exception e);
        void onReadFailure_ModbusNumberException(Exception e);
        void onReadFailure_ModbusIOException(Exception e);

        void onWriteSuccess();
        void onWriteFailure_ModbusProtocolException(Exception e);
        void onWriteFailure_ModbusNumberException(Exception e);
        void onWriteFailure_ModbusIOException(Exception e);
    }
    private ModbusMasterCallback modbusMasterCallback = new ModbusMasterCallback() {
        @Override
        public void onConnectSuccess(ModbusMaster master) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "ModbusTCP连接成功",Toast.LENGTH_SHORT).show();
                    button_connect.setEnabled(false);
                    button_disconnect.setEnabled(true);
                    button_readMB.setEnabled(true);
                    button_writeMB.setEnabled(true);
                    //持久化保存IP、端口、从机地址
                    //持久化保存IP、端口、从机地址
                    SharedPreferences preferences = getSharedPreferences("Preference",0);
                    preferences.edit().putString("ip",input_IP.getText().toString()).apply();
                    preferences.edit().putString("port",input_port.getText().toString()).apply();
                }
            });
        }

        @Override
        public void onConnectFailure(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "ModbusTCP连接失败:",Toast.LENGTH_SHORT).show();
                    button_connect.setEnabled(true);
                    button_disconnect.setEnabled(false);
                    button_readMB.setEnabled(false);
                    button_writeMB.setEnabled(false);
                }
            });
        }

        @Override
        public void onReadSuccess() {

        }

        @Override
        public void onReadFailure_ModbusProtocolException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "读取失败，请确定从站定义类型是否相同，异常信息："+e.getMessage(),Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onReadFailure_ModbusNumberException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "读取失败，请确定数字格式是否正确，异常信息："+e.getMessage(),Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onReadFailure_ModbusIOException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "读取失败，异常信息："+e.getMessage(),Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onWriteSuccess() {

        }

        @Override
        public void onWriteFailure_ModbusProtocolException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "写入失败，请确定从站定义类型是否相同，异常信息："+e.getMessage(),Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onWriteFailure_ModbusNumberException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "写入失败，请确定数字类型是否正确，异常信息："+e.getMessage(),Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onWriteFailure_ModbusIOException(Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "写入失败，异常信息："+e.getMessage(),Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
                selectFile();
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
            Toast.makeText(MainActivity.this,"没有连接WIFI，请连接WIFI",Toast.LENGTH_LONG).show();
    }

    //检查WIF是否打开
    public void checkWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if(wifiManager.isWifiEnabled())
            Toast.makeText(MainActivity.this,"没有打开WIFI，请打开WIFI",Toast.LENGTH_LONG).show();
    }


    //请求读取本地文件权限
    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 权限未被授予，进行请求
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE_File);
        } else {
            // 权限已被授予，可以执行相关操作
            selectFile();
        }
    }
    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // 设置文件类型为任意文件
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择配置文件"), 1);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                // 处理文件
                handleSelectedFile(uri);
            }
        }
    }
    private void handleSelectedFile(Uri uri) {
        // 读取文件的内容或路径
        String path = uri.getPath();
        Toast.makeText(this, "选中的文件路径: " + path, Toast.LENGTH_SHORT).show();
        // 这里可以添加更多的处理逻辑，读取文件内容等

    }

    //读取本地参数
}