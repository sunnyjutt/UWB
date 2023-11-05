package com.example.uwbtestassessment.helpers;

import androidx.core.uwb.RangingPosition;
import androidx.core.uwb.UwbAddress;

import com.clj.fastble.data.BleDevice;

import io.reactivex.rxjava3.disposables.Disposable;

public class UwbBleDevice {
    public BleDevice bleDev;
    public UwbAddress uwbDevAddr;
    public RangingPosition uwbPosition;
    public Disposable disposable;
}
