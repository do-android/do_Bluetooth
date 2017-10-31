package dotest.module.activity;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;

import com.doext.module.activity.R;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import core.DoServiceContainer;
import doext.implement.do_Bluetooth_Model;
import dotest.module.frame.debug.DoService;

public class BleTestActivity extends DoTestActivity {
	
	private EditText et_content;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		et_content = (EditText) findViewById(R.id.content);
	}
	
	@Override
	protected void initModuleModel() throws Exception {
		this.model = new do_Bluetooth_Model();
	}

	public void open(View view) {
		DoService.syncMethod(this.model, "open", new HashMap<String, String>());
	}
	
	public void write(View view) {
		Map<String, String>  _paras = new HashMap<String, String>();
		_paras.put("data", "123456abcde");
		_paras.put("charUUID", "5216B2C4-0940-480A-89AA-D6FEAEDBDBB8");
		DoService.syncMethod(this.model, "write", _paras);
	}
	
	public void close(View view) {
		Map<String, String>  _paras = new HashMap<String, String>();
		DoService.syncMethod(this.model, "close", _paras);
	}

	public void connect(View view) {
		Map<String, Object>  _paras_sendString = new HashMap<String, Object>();
		_paras_sendString.put("address", "4E:37:B7:F2:C2:9E");
		JSONArray arr = new JSONArray();
		arr.put("040BCECC-1992-425F-AFFB-74E376CE86F8");
		_paras_sendString.put("serviceUUIDs", arr);
        DoService.asyncMethod(this.model, "connect", _paras_sendString, new DoService.EventCallBack() {
			@Override
			public void eventCallBack(String _data) {//回调函数
				DoServiceContainer.getLogEngine().writeDebug("异步方法回调：" + _data);
			}
		});
	}

	@Override
	protected void onEvent() {
		DoService.subscribeEvent(this.model, "characteristicChanged", new DoService.EventCallBack() {
			@Override
			public void eventCallBack(String _data) {
				DoServiceContainer.getLogEngine().writeDebug("事件回调：" + _data);
				et_content.setText(et_content.getText() + "/r/n" + "characteristicChanged->" + _data);
			}
		});
		DoService.subscribeEvent(this.model, "scan", new DoService.EventCallBack() {
			@Override
			public void eventCallBack(String _data) {
				DoServiceContainer.getLogEngine().writeDebug("事件回调：" + _data);
				et_content.setText(et_content.getText() + "/r/n" + "scan->" + _data);
			}
		});
		DoService.subscribeEvent(this.model, "connectionStateChange", new DoService.EventCallBack() {
			@Override
			public void eventCallBack(String _data) {
				DoServiceContainer.getLogEngine().writeDebug("事件回调：" + _data);
				et_content.setText(et_content.getText() + "/r/n" + "connectionStateChange->" + _data);
			}
		});
	}
	
}
