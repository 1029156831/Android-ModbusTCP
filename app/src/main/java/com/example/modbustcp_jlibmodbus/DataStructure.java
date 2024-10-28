package com.example.modbustcp_jlibmodbus;
import com.google.gson.annotations.SerializedName;

public class DataStructure {
    @SerializedName("功能")
    private String functionName;
    @SerializedName("状态")
    private String status;
    @SerializedName("MODBUS地址")
    private String modbusAddress;
    @SerializedName("位")
    private String bit;
    @SerializedName("单位")
    private String unit;

    // Getters 和 Setters
    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModbusAddress() { return modbusAddress; }
    public void setModbusAddress(String modbusAddress) { this.modbusAddress = modbusAddress; }
    public String getBit() { return bit; }
    public void setBit(String bit) { this.bit = bit; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
