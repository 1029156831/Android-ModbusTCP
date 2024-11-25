package com.example.modbustcp_jlibmodbus.viewmodel;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;

public class SharedViewModel extends ViewModel {
    private final MutableLiveData<String> modbusAddress = new MutableLiveData<>();
    public void setModbusAddress(String address) {
        modbusAddress.setValue(address);
    }
    public LiveData<String> getModbusAddress() {
        return modbusAddress;
    }

    private final MutableLiveData<String> input_slave_address = new MutableLiveData<>();
    public void setInput_slave_address(String address) {
        input_slave_address.setValue(address);
    }
    public LiveData<String> getInput_slave_address() {
        return input_slave_address;
    }

    private final MutableLiveData<Boolean> isRunning = new MutableLiveData<>();
    public void setIsRunning(boolean isRunningValue) {
        isRunning.setValue(isRunningValue);
    }
    public LiveData<Boolean> getIsRunning() {
        return isRunning;
    }

    private final MutableLiveData<ModbusMaster> modbusMasterLiveData = new MutableLiveData<>();
    public void setModbusMaster(ModbusMaster modbusMaster) {
        //modbusMasterLiveData.setValue(modbusMaster);
        modbusMasterLiveData.postValue(modbusMaster);
    }

    public LiveData<ModbusMaster> getModbusMaster() {
        return modbusMasterLiveData;
    }

    private final MutableLiveData<Boolean> button_start_read_Enabled = new MutableLiveData<>();
    public void setButtonStartReadEnabled(boolean isEnabled) {
        button_start_read_Enabled.setValue(isEnabled);
    }

    public LiveData<Boolean> getButtonStartReadEnabled() {
        return button_start_read_Enabled;
    }

    private final MutableLiveData<Integer> button_start_read_BackgroundColor = new MutableLiveData<>();

    public void setButtonStartReadBackgroundColor(int color) {
        button_start_read_BackgroundColor.setValue(color);
    }

    public LiveData<Integer> getButtonStartReadBackgroundColor() {
        return button_start_read_BackgroundColor;
    }


    private final MutableLiveData<Boolean> button_stop_read_Enabled = new MutableLiveData<>();
    public void setButtonStopReadEnabled(boolean isEnabled) {
        button_stop_read_Enabled.setValue(isEnabled);
    }

    public LiveData<Boolean> getButtonStopReadEnabled() {
        return button_stop_read_Enabled;
    }

    private final MutableLiveData<Integer> button_stop_read_BackgroundColor = new MutableLiveData<>();

    public void setButtonStopReadBackgroundColor(int color) {
        button_stop_read_BackgroundColor.setValue(color);
    }

    public LiveData<Integer> getButtonStopReadBackgroundColor() {
        return button_stop_read_BackgroundColor;
    }


    private final MutableLiveData<Boolean> button_connect_Enabled = new MutableLiveData<>();
    public void setButtonConnectEnabled(boolean isEnabled) {
        button_connect_Enabled.setValue(isEnabled);
    }

    public LiveData<Boolean> getButtonConnectEnabled() {
        return button_connect_Enabled;
    }

    private final MutableLiveData<Integer> button_connect_BackgroundColor = new MutableLiveData<>();
    public void setButtonConnectBackgroundColor(int color) {
        button_connect_BackgroundColor.setValue(color);
    }

    public LiveData<Integer> getButtonConnectBackgroundColor() {
        return button_connect_BackgroundColor;
    }


    private final MutableLiveData<Boolean> button_disconnect_Enabled = new MutableLiveData<>();
    public void setButtonDisconnectEnabled(boolean isEnabled) {
        button_disconnect_Enabled.setValue(isEnabled);
    }

    public LiveData<Boolean> getButtonDisconnectEnabled() {
        return button_disconnect_Enabled;
    }

    private final MutableLiveData<Integer> button_disconnect_BackgroundColor = new MutableLiveData<>();

    public void setButtonDisconnectBackgroundColor(int color) {
        button_disconnect_BackgroundColor.setValue(color);
    }

    public LiveData<Integer> getButtonDisconnectBackgroundColor() {
        return button_disconnect_BackgroundColor;
    }

    private final MutableLiveData<Integer> firstFragmentTabLayoutHeight = new MutableLiveData<>();

    public void setFirstFragmentTabLayoutHeight(int height) {
        firstFragmentTabLayoutHeight.setValue(height);
    }

    public LiveData<Integer> getFirstFragmentTabLayoutHeight() {
        return firstFragmentTabLayoutHeight;
    }

    private final MutableLiveData<Integer> secondFragmentTabLayoutHeight = new MutableLiveData<>();

    public void setSecondFragmentTabLayoutHeight(int height) {
        secondFragmentTabLayoutHeight.setValue(height);
    }

    public LiveData<Integer> getSecondFragmentTabLayoutHeight() {
        return secondFragmentTabLayoutHeight;
    }

    private final MutableLiveData<Integer> thirdFragmentTabLayoutHeight = new MutableLiveData<>();

    public void setThirdFragmentTabLayoutHeight(int height) {
        thirdFragmentTabLayoutHeight.setValue(height);
    }

    public LiveData<Integer> getThirdFragmentTabLayoutHeight() {
        return thirdFragmentTabLayoutHeight;
    }
}
