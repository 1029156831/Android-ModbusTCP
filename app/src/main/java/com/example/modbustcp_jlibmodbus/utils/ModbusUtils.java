package com.example.modbustcp_jlibmodbus.utils;

import android.content.res.ColorStateList;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.view.ViewCompat;

import com.example.modbustcp_jlibmodbus.R;
import com.example.modbustcp_jlibmodbus.activity.Engineering_mode_Activity;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ModbusUtils {

    //JSON全局配置
    //数据格式顺序（字节转换顺序），默认为CDAB，big-endian值为ABCD，little-endian值为DCBA，big-endian-swap为BADC，little-endian-swap为CDAB，
    public static String data_format_order ="CDAB";

    // 辅助方法：处理值的写入
    private void handleValueWrite(ModbusMaster modbusMaster, String modbusAddress, EditText inputSlaveAddress, String unit, String newValue, Float scaleFactor_float) {
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
//                    if (modbusMasterCallback != null) {
//                        switch (throwable.getClass().getSimpleName()){
//                            case "ModbusProtocolException":
//                                modbusMasterCallback.onWriteFailure_ModbusProtocolException(new Exception(throwable));
//                                break;
//                            case "ModbusNumberException":
//                                modbusMasterCallback.onWriteFailure_ModbusNumberException(new Exception(throwable));
//                                break;
//                            case "ModbusIOException":
//                                modbusMasterCallback.onWriteFailure_ModbusIOException(new Exception(throwable));
//                                break;
//                            case "NumberFormatException":
//                                modbusMasterCallback.onWriteFailure_NumberFormatException(new Exception(throwable));
//                                break;
//                            case "IllegalArgumentException":
//                                modbusMasterCallback.onWriteFailure_IllegalArgumentException(new Exception(throwable));
//                                break;
//                            case "TimeoutException":
//                                modbusMasterCallback.onWriteFailure_TimeoutException(new Exception(throwable));
//                                break;
//                            default:
//                                modbusMasterCallback.onWriteFailure_Exception(new Exception(throwable));
//                                break;
//                        }
//                    }
                });
    }

    //转换为小端处理
    public int[] floatToRegisters(float value) {
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

}
