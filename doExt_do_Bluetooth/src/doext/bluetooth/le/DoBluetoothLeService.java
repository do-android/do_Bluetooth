/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package doext.bluetooth.le;

import java.util.UUID;

import core.DoServiceContainer;
import android.R.integer;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
public class DoBluetoothLeService extends Service {
	private final static String TAG = DoBluetoothLeService.class.getSimpleName();
	private PendingWrite mPendingWrite;

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;
	public static final int STATE_DISCONNECTED = 0;
	public static final int STATE_CONNECTING = 1;
	public static final int STATE_CONNECTED = 2;

	public final static String ACTION_GATT_CONNECTED = "do.ext.bluetooth.le.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "do.ext.bluetooth.le.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "do.ext.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE = "do.ext.bluetooth.le.ACTION_DATA_AVAILABLE";

	public final static String EXTRA_DATA = "do.ext.bluetooth.le.EXTRA_DATA";
	public final static String CHARACTERISTIC_UUID = "do.ext.bluetooth.le.CHARACTERISTIC_UUID";

	public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(DoGattAttributes.HEART_RATE_MEASUREMENT);

	// Implements callback methods for GATT events that the app cares about. For
	// example,connection change and services discovered.
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			String intentAction;
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = ACTION_GATT_CONNECTED;
				broadcastUpdate(intentAction);
				Log.d(TAG, "ble device connected");
				// Attempts to discover services after successful connection.
				boolean isSuccess = mBluetoothGatt.discoverServices();
				Log.d(TAG, "Attempting to start service discovery:" + isSuccess);

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = ACTION_GATT_DISCONNECTED;
				Log.d(TAG, "ble device disconnected");
				broadcastUpdate(intentAction);
				if (mBluetoothGatt != null) {
					mBluetoothGatt.disconnect();
					mBluetoothGatt.close();
				}
			} else if (newState == BluetoothProfile.STATE_CONNECTING) {
			} else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
				if (mBluetoothGatt != null) {
					mBluetoothGatt.close();
				}
			}
		}

		@Override
		public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
			Log.d(TAG, " service discovered and status = " + status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (mPendingWrite == null) {
					return;
				}
				Object followStr = mPendingWrite.getNextContent();
				if (followStr == null) {
					Log.d(TAG, "write buffer success:" + mPendingWrite.toString());
					mPendingWrite = null;
				} else {
					Log.d(TAG, "write buffer part:" + followStr);
					if (followStr instanceof byte[]) {
						characteristic.setValue((byte[]) followStr);
					} else {
						characteristic.setValue(followStr.toString());
					}
					mBluetoothGatt.writeCharacteristic(characteristic);
				}
			} else {
				Log.w(TAG, "write char fail");
			}

		};

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
		}
	};

	private void broadcastUpdate(final String action) {
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
	}

	private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
		final Intent intent = new Intent(action);
		byte[] data = characteristic.getValue();
		if (data != null && data.length > 0) {
			String value = new String(data, 0, data.length);
			Log.d(TAG, "data.length=" + data.length + "----" + value);
			intent.putExtra(EXTRA_DATA, value);
			intent.putExtra(CHARACTERISTIC_UUID, characteristic.getUuid().toString());
		}
		sendBroadcast(intent);
	}

	public class LocalBinder extends Binder {
		public DoBluetoothLeService getService() {
			return DoBluetoothLeService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// After using a given device, you should make sure that
		// BluetoothGatt.close() is called
		// such that resources are cleaned up properly. In this particular
		// example, close() is
		// invoked when the UI is disconnected from the Service.
		close();
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 *
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter
		// through
		// BluetoothManager.
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address
	 *            The device address of the destination device.
	 *
	 * @return Return true if the connection is initiated successfully. The
	 *         connection result is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */
	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			DoServiceContainer.getLogEngine().writeDebug("未初始化蓝牙组件,请执行open方法");
			Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		// Previously connected device. Try to reconnect.
		if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
			Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
			if (mBluetoothGatt.connect()) {
//                mConnectionState = STATE_CONNECTING;
				return true;
			} else {
				return false;
			}
		}

		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		// We want to directly connect to the device, so we are setting the
		// autoConnect parameter to false.
		mBluetoothGatt = device.connectGatt(null, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
//        mConnectionState = STATE_CONNECTING;
		return true;
	}

	public static String bytesToHexString(byte[] src) {
		StringBuilder stringBuilder = new StringBuilder("");
		if (src == null || src.length <= 0) {
			return "";
		}
		for (int i = 0; i < src.length; i++) {
			int v = src[i] & 0xFF;
			String hv = Integer.toHexString(v);
			if (hv.length() < 2) {
				stringBuilder.append(0);
			}
			stringBuilder.append(hv);
		}
		return stringBuilder.toString();
	}

	public static byte[] hex2Byte(String paramString) {
		char[] arrayOfChar = paramString.toCharArray();
		byte[] arrayOfByte = new byte[paramString.length() / 2];
		for (int i = 0;; i++) {
			if (i >= arrayOfByte.length)
				return arrayOfByte;
			arrayOfByte[i] = ((byte) (0xFF & 16 * "0123456789ABCDEF".indexOf(arrayOfChar[(i * 2)]) + "0123456789ABCDEF".indexOf(arrayOfChar[(1 + i * 2)])));
		}
	}

	public int writeValue(PendingWrite pw, boolean isFrist, String sUUID, String cUUID) {
		if (mBluetoothManager == null) {
			Log.w(TAG, "Unable to initialize BluetoothManager");
			return -1;
		}
		if ((this.mBluetoothAdapter == null) || (this.mBluetoothGatt == null)) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return 1;
		}
		Object result = getGattCharacteristic(sUUID, cUUID);

		if (!(result instanceof BluetoothGattCharacteristic)) {
			return (Integer) result;
		}

		BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) result;
		if (isFrist) {
			mBluetoothGatt.setCharacteristicNotification(characteristic, true);
		}
		mPendingWrite = pw;
		Object fristText = pw.getNextContent();
		Log.d(TAG, "write buffer part:" + fristText);
		if (fristText instanceof byte[]) {
			characteristic.setValue((byte[]) fristText);
		} else {
			characteristic.setValue(fristText.toString());
		}
		boolean isWriteOk = mBluetoothGatt.writeCharacteristic(characteristic);
		Log.d(TAG, "writeCharacteristic " + isWriteOk);
		if (isWriteOk) {
			return 0;
		}
		return -1;
	}

	public int readValue(boolean isFrist, String sUUID, String cUUID) {
		if (mBluetoothManager == null) {
			Log.w(TAG, "Unable to initialize BluetoothManager");
			return -1;
		}
		if ((this.mBluetoothAdapter == null) || (this.mBluetoothGatt == null)) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return 1;
		}
		Object result = getGattCharacteristic(sUUID, cUUID);

		if (!(result instanceof BluetoothGattCharacteristic)) {
			return (Integer) result;
		}

		BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) result;
		boolean isReadOk = mBluetoothGatt.readCharacteristic(characteristic);
		Log.d(TAG, "readCharacteristic " + isReadOk);
		if (isReadOk) {
			return 0;
		}
		return -1;
	}

	String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

	public int setListener(boolean isFrist, String sUUID, String cUUID) {
		if (mBluetoothManager == null) {
			Log.w(TAG, "Unable to initialize BluetoothManager");
			return -1;
		}
		if ((this.mBluetoothAdapter == null) || (this.mBluetoothGatt == null)) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return 1;
		}
		Object result = getGattCharacteristic(sUUID, cUUID);

		if (!(result instanceof BluetoothGattCharacteristic)) {
			return (Integer) result;
		}
		try {
			BluetoothGattCharacteristic characteristic = (BluetoothGattCharacteristic) result;
			if (characteristic.getDescriptors().size() != 0) {
				String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
				BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				mBluetoothGatt.writeDescriptor(descriptor);
			}
			int properties = characteristic.getProperties();
			if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
				mBluetoothGatt.setCharacteristicNotification(characteristic, false);
			}
			if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
				mBluetoothGatt.setCharacteristicNotification(characteristic, false);
			}
			if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
				mBluetoothGatt.setCharacteristicNotification(characteristic, false);
			}
			if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
				mBluetoothGatt.setCharacteristicNotification(characteristic, true);
			}
			return 0;
		} catch (Exception e) {
			return -1;
		}
	}

	private Object getGattCharacteristic(String sUUID, String cUUID) {
		if (mBluetoothGatt == null) {
			Log.w(TAG, "gatt not ready to get character");
			return 1;
		}
		BluetoothGattService mGattService = null;
		BluetoothGattCharacteristic mGattCharacteristic = null;
		try {
			mGattService = mBluetoothGatt.getService(UUID.fromString(sUUID));
		} catch (Exception e) {
			Log.w(TAG, "service not found");
			return 2;
		}
		try {
			mGattCharacteristic = mGattService.getCharacteristic(UUID.fromString(cUUID));
		} catch (Exception e) {
			Log.w(TAG, "character not found");
			return 3;
		}
		return mGattCharacteristic;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The
	 * disconnection result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.disconnect();
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure
	 * resources are released properly.
	 */
	public void close() {
		if (mBluetoothGatt != null) {
			mBluetoothGatt.close();
			mBluetoothGatt = null;
		}
		if (mBluetoothAdapter != null) {
			mBluetoothAdapter = null;
		}
		if (mBluetoothManager != null) {
			mBluetoothManager = null;
		}
	}

	public abstract class PendingWrite {
		public abstract Object getNextContent();
	}

	public class PendingWriteText extends PendingWrite {
		public PendingWriteText(String info, int maxLen) {
			text = info;
			length = 0;
			maxLength = maxLen;
		}

		private String text;
		private int length;
		private int maxLength;

		public String getNextContent() {
			if (length >= text.length())
				return null;
			if ((length + maxLength) >= text.length()) {
				String nextStr = text.substring(length, text.length());
				length = text.length();
				return nextStr;
			} else {
				String nextStr = text.substring(length, length + maxLength);
				length += maxLength;
				return nextStr;
			}
		}
	}

	public class PendingWriteBinary extends PendingWrite {
		// info是十六进制表示的二进制数据
		public PendingWriteBinary(String info, int maxLen) {
			text = hex2Byte(info);
			length = 0;
			maxLength = maxLen;
		}

		private byte[] text;
		private int length;
		private int maxLength;

		public byte[] getNextContent() {
			if (length >= text.length)
				return null;
			if ((length + maxLength) >= text.length) {
//				byte[] nextStr = text.substring(length, text.length);
				byte[] nextStr = new byte[text.length - length];
				System.arraycopy(text, length, nextStr, 0, nextStr.length);
				length = text.length;
				return nextStr;
			} else {
				byte[] nextStr = new byte[maxLength];
//				byte[] nextStr = text.substring(length, length + maxLength);
				System.arraycopy(text, length, nextStr, 0, nextStr.length);
				length += maxLength;
				return nextStr;
			}
		}
	}
}
