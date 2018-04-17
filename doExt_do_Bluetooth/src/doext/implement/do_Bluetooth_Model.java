package doext.implement;

import org.json.JSONObject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import core.DoServiceContainer;
import core.helper.DoJsonHelper;
import core.interfaces.DoActivityResultListener;
import core.interfaces.DoIPageView;
import core.interfaces.DoIScriptEngine;
import core.object.DoEventCenter;
import core.object.DoInvokeResult;
import doext.bluetooth.le.DoBluetoothLeService;
import doext.bluetooth.le.DoBluetoothLeService.PendingWrite;
import doext.define.do_Bluetooth_MAbstract;

/**
 * 自定义扩展MM组件Model实现，继承do_Bluetooth_MAbstract抽象类，并实现do_Bluetooth_IMethod接口方法；
 * #如何调用组件自定义事件？可以通过如下方法触发事件：
 * this.model.getEventCenter().fireEvent(_messageName, jsonResult);
 * 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象； 获取DoInvokeResult对象方式new
 * DoInvokeResult(this.getUniqueKey());
 */
public class do_Bluetooth_Model extends do_Bluetooth_MAbstract implements DoActivityResultListener {

	private static final String TAG = "do_Bluetooth";
	private static final int REQUEST_ENABLE_BT = 1;
	private Context mContext;
	private BluetoothAdapter mBluetoothAdapter;
	private DoBluetoothLeService mBluetoothLeService;

	public do_Bluetooth_Model() throws Exception {
		super();
		this.mContext = DoServiceContainer.getPageViewFactory().getAppContext();
		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		((DoIPageView) mContext).registActivityResultListener(this);

	}

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((DoBluetoothLeService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				return;
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device. This can be a
	// result of read
	// or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
			if (blueState == BluetoothAdapter.STATE_OFF) {
				// 手动关闭蓝牙
				mBluetoothLeService.close();
				mContext.unbindService(mServiceConnection);
				fireconnectionStateChange(0);
			}
			if (blueState == BluetoothAdapter.STATE_ON) {
				fireconnectionStateChange(1);
			}
			if (DoBluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

			} else if (DoBluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				fireconnectionStateChange(BluetoothProfile.STATE_DISCONNECTED);
			} else if (DoBluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				fireconnectionStateChange(1);
			} else if (DoBluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				String data = intent.getStringExtra(DoBluetoothLeService.EXTRA_DATA);
				String uuid = intent.getStringExtra(DoBluetoothLeService.CHARACTERISTIC_UUID);
				// fire
				JSONObject jsonNode = new JSONObject();
				try {
					jsonNode.put("uuid", uuid);
					jsonNode.put("value", data);
				} catch (Exception e) {
					e.printStackTrace();
				}
				fireOther("characteristicChanged", jsonNode);
			}
		}
	};

	/**
	 * 同步方法，JS脚本调用该组件对象方法时会被调用，可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V），获取参数值使用API提供DoJsonHelper类；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public boolean invokeSyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if ("close".equals(_methodName)) {
			close(_dictParas, _scriptEngine, _invokeResult);
			return true;
		} else if ("enable".equals(_methodName)) {
			enable(_dictParas, _scriptEngine, _invokeResult);
			return true;
		} else if ("disable".equals(_methodName)) {
			disable(_dictParas, _scriptEngine, _invokeResult);
			return true;
		} else if ("stopScan".equals(_methodName)) {
			stopScan(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		return super.invokeSyncMethod(_methodName, _dictParas, _scriptEngine, _invokeResult);
	}

	@Override
	public boolean invokeAsyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		if ("open".equals(_methodName)) {
			this.open(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		if ("startScan".equals(_methodName)) {
			this.startScan(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		if ("connect".equals(_methodName)) {
			this.connect(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		if ("write".equals(_methodName)) {
			this.write(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		if ("read".equals(_methodName)) {
			this.read(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		if ("registerListener".equals(_methodName)) {
			this.registerListener(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		return super.invokeAsyncMethod(_methodName, _dictParas, _scriptEngine, _callbackFuncName);
	}

	private void fireconnectionStateChange(int result) {
		DoEventCenter eventCenter = getEventCenter();
		if (eventCenter != null) {
			DoInvokeResult _invokeResult = new DoInvokeResult(getUniqueKey());
			_invokeResult.setResultInteger(result);
			eventCenter.fireEvent("connectionStateChange", _invokeResult);
		}
	}

	private void fireOther(String eventName, JSONObject jsonObject) {
		DoEventCenter eventCenter = getEventCenter();
		if (eventCenter != null) {
			DoInvokeResult _invokeResult = new DoInvokeResult(getUniqueKey());
			_invokeResult.setResultNode(jsonObject);
			eventCenter.fireEvent(eventName, _invokeResult);
		}
	}

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
			((Activity) mContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (device == null) {
						return;
					}
					// fire
					JSONObject jsonNode = new JSONObject();
					try {
						jsonNode.put("address", device.getAddress());
						if (null == device.getName())
							jsonNode.put("name", "");
						else
							jsonNode.put("name", device.getName());
						jsonNode.put("RSSI", rssi);
					} catch (Exception _err) {
						DoServiceContainer.getLogEngine().writeError("do_Bluetooth_Model scan event \n\t", _err);
					}
					fireOther("scan", jsonNode);
				}
			});
		}
	};
	private boolean isBind = false;

	/**
	 * 打开中心设备蓝牙；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	public void open(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		// Use this check to determine whether BLE is supported on the device.
		// Then you can
		// selectively disable BLE-related features.
		if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			// 不支持蓝牙BLE4.0，android4.3.0以上才支持LE
			DoServiceContainer.getLogEngine().writeInfo("BLE is not supported", TAG);
			callBack(1, _scriptEngine, _callbackFuncName);
			return;
		}
		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			// 没有支持的蓝牙设备
			DoServiceContainer.getLogEngine().writeInfo("Bluetooth not supported", TAG);
			callBack(2, _scriptEngine, _callbackFuncName);
			return;
		}
		// Ensures Bluetooth is enabled on the device. If Bluetooth is not
		// currently enabled,
		// fire an intent to display a dialog asking the user to grant
		// permission to enable it.
		if (!mBluetoothAdapter.isEnabled()) {
			callBack(3, _scriptEngine, _callbackFuncName);
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			((Activity) mContext).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			callBack(0, _scriptEngine, _callbackFuncName);
			mContext.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
			Intent gattServiceIntent = new Intent(mContext, DoBluetoothLeService.class);
			isBind = mContext.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		}
	}

	private void callBack(Object object, DoIScriptEngine _scriptEngine, String _callbackFuncName) {
		DoInvokeResult _invokeResult = new DoInvokeResult(getUniqueKey());
		if (object instanceof Boolean) {
			_invokeResult.setResultBoolean((Boolean) object);
		} else {
			_invokeResult.setResultInteger((Integer) object);
		}
		_scriptEngine.callback(_callbackFuncName, _invokeResult);
	}

	public void startScan(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		callBack(mBluetoothAdapter.startLeScan(mLeScanCallback), _scriptEngine, _callbackFuncName);
	}

	public void stopScan(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
	}

	/**
	 * 关闭蓝牙连接；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	public void close(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (mGattUpdateReceiver != null) {
			mContext.unregisterReceiver(mGattUpdateReceiver);
		}

		if (mBluetoothLeService != null) {
			mBluetoothLeService.disconnect();
			mBluetoothLeService.close();
		}
		mContext.unbindService(mServiceConnection);
		fireconnectionStateChange(0);
	}

	private boolean enableBle() {
		if (mBluetoothAdapter.isEnabled())
			return true;
		return mBluetoothAdapter.enable();
	}

	private boolean disableBle() {
		if (mBluetoothAdapter.isEnabled()) {
			return mBluetoothAdapter.disable();
		}
		return true;
	}

	public void enable(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (mBluetoothAdapter == null) {
			// 没有支持的蓝牙设备
			_invokeResult.setError("设备不支持蓝牙功能");
			return;
		}
		_invokeResult.setResultBoolean(enableBle());
	}

	public void disable(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (mBluetoothAdapter == null) {
			// 没有支持的蓝牙设备
			_invokeResult.setError("设备不支持蓝牙功能");
			return;
		}
		_invokeResult.setResultBoolean(disableBle());
	}

	/**
	 * 连接外围设备；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_callbackFuncName 回调函数名
	 */
	public void connect(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
		String mDeviceAddress = DoJsonHelper.getString(_dictParas, "address", "");
		if (mBluetoothLeService != null) {
			// 需要放到主线程中执行
			boolean result = mBluetoothLeService.connect(mDeviceAddress);
			callBack(result, _scriptEngine, _callbackFuncName);
			if (!result) {
				DoServiceContainer.getLogEngine().writeInfo("连接失败，address:" + mDeviceAddress, TAG);
			}
			Log.d(TAG, "Connect request result=" + result);
		}
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(DoBluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(DoBluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(DoBluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(DoBluetoothLeService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(DoBluetoothLeService.CHARACTERISTIC_UUID);
		intentFilter.addAction(DoBluetoothLeService.EXTRA_DATA);
		intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		return intentFilter;
	}

	/**
	 * 写入数据；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_callbackFuncName 回调函数名
	 */
	public void write(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		String data = DoJsonHelper.getString(_dictParas, "data", "");
		String sUUID = DoJsonHelper.getString(_dictParas, "sUUID", "");
		String cUUID = DoJsonHelper.getString(_dictParas, "cUUID", "");
		String type = DoJsonHelper.getString(_dictParas, "type", "string"); // binary
		int length = DoJsonHelper.getInt(_dictParas, "length", 20);

		PendingWrite pw = null;
		if ("binary".equals(type)) {
			pw = mBluetoothLeService.new PendingWriteBinary(data, length);
		} else {
			pw = mBluetoothLeService.new PendingWriteText(data, length);
		}

		int _result = mBluetoothLeService.writeValue(pw, true, sUUID, cUUID);
		callBack(_result, _scriptEngine, _callbackFuncName);
	}

	/**
	 * 读取数据；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_callbackFuncName 回调函数名
	 */
	public void read(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		String sUUID = DoJsonHelper.getString(_dictParas, "sUUID", "");
		String cUUID = DoJsonHelper.getString(_dictParas, "cUUID", "");
//		int length = DoJsonHelper.getInt(_dictParas, "length", 20);
//		PendingWrite pw = mBluetoothLeService.new PendingWrite(data, length);
		int _result = mBluetoothLeService.readValue(true, sUUID, cUUID);
		callBack(_result, _scriptEngine, _callbackFuncName);
	}

	public void registerListener(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		String sUUID = DoJsonHelper.getString(_dictParas, "sUUID", "");
		String cUUID = DoJsonHelper.getString(_dictParas, "cUUID", "");
		int _result = mBluetoothLeService.setListener(true, sUUID, cUUID);
		callBack(_result, _scriptEngine, _callbackFuncName);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
			return;
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		if (isBind) {
			mContext.unbindService(mServiceConnection);
			mBluetoothLeService = null;
			isBind = false;
		}
	}
}