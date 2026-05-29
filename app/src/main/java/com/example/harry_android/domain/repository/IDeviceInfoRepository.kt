package com.example.harry_android.domain.repository

import com.example.harry_android.domain.model.DeviceInfo

interface IDeviceInfoRepository {
    suspend fun readDeviceInfo(): DeviceInfo
}