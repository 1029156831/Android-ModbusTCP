package com.example.modbustcp_jlibmodbus;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {
    private List<DataStructure> dataList;

    public DataAdapter(List<DataStructure> dataList) {
        this.dataList = dataList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_datastructure, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DataStructure data = dataList.get(position);
        if (data != null) {
            holder.functionName.setText(data.getFunctionName() != null ? data.getFunctionName() : "无数据");
            holder.status.setText(data.getStatus() != null ? data.getStatus() : "无数据");
            holder.modbusAddress.setText(data.getModbusAddress() != null ? data.getModbusAddress() : "无数据");
            holder.bit.setText(data.getBit() != null ? data.getBit() : "无数据");
            holder.unit.setText(data.getUnit() != null ? data.getUnit() : "无数据");
        }
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView functionName, status, modbusAddress, bit, unit;

        public ViewHolder(View itemView) {
            super(itemView);
            functionName = itemView.findViewById(R.id.text_function_name);
            status = itemView.findViewById(R.id.text_status);
            modbusAddress = itemView.findViewById(R.id.text_modbus_address);
            bit = itemView.findViewById(R.id.text_bit);
            unit = itemView.findViewById(R.id.text_unit);
        }
    }
}
