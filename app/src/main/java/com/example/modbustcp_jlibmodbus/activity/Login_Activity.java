package com.example.modbustcp_jlibmodbus.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.example.modbustcp_jlibmodbus.R;

public class Login_Activity extends AppCompatActivity{
    Button button_fault_diagnosis,button_engineering_mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login); // 确保存在对应的布局文件 activity_login.xml

        button_fault_diagnosis = findViewById(R.id.button_fault_diagnosis);
        button_engineering_mode = findViewById(R.id.button_engineering_mode);


        button_fault_diagnosis.setOnClickListener(v -> {
            Intent intent = new Intent(Login_Activity.this, Fault_diagnosis_Activity.class);
            startActivity(intent);
        });

        final View.OnClickListener selectListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == button_fault_diagnosis) {
                    Intent intent = new Intent(Login_Activity.this, Fault_diagnosis_Activity.class);
                    startActivity(intent);
                } else if (v == button_engineering_mode) {
                    Intent intent = new Intent(Login_Activity.this, Engineering_mode_Activity.class);
                    startActivity(intent);
                }
            }
        };

        button_fault_diagnosis.setOnClickListener(selectListener);
        button_engineering_mode.setOnClickListener(selectListener);
    }
}
