package com.novadart.reactnativenfc;
import java.util.Locale;
import java.io.ByteArrayOutputStream;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NdefMessage;
//import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.NdefRecord;
import android.os.AsyncTask;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import android.util.Log;
import android.os.Handler;
import android.nfc.tech.*;

import com.facebook.react.bridge.ActivityEventListener;
//import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.novadart.reactnativenfc.parser.NdefParser;
import com.novadart.reactnativenfc.parser.TagParser;

public class ReactNativeNFCModule extends ReactContextBaseJavaModule
        implements ActivityEventListener,LifecycleEventListener {

    private static final String EVENT_NFC_DISCOVERED = "__NFC_DISCOVERED";
    private static final String NFC_OUT_OF_RANGE = "__NFC_OUT_OF_RANGE";
    private static final String EVENT_NFC_ERROR = "__NFC_ERROR";
    private static final String EVENT_NFC_MISSING = "__NFC_MISSING";
    private static final String EVENT_NFC_UNAVAILABLE = "__NFC_UNAVAILABLE";
    private static final String EVENT_NFC_ENABLED = "__NFC_ENABLED";
    private static final String[][] techList = new String[][]{ new String[] {
        IsoDep.class.getName(),
        NfcA.class.getName(),
        NfcB.class.getName(),
        NfcF.class.getName(),
        NfcV.class.getName(),
        Ndef.class.getName(),
        // NfcBarcode.class.getName(),
        NdefFormatable.class.getName(),
        MifareClassic.class.getName(),
        MifareUltralight.class.getName()
    } };

    // caches the last message received, to pass it to the listeners when it reconnects
    private NfcAdapter adapter;
    private WritableMap startupNfcData;
    private boolean startupNfcDataRetrieved = false;
    private boolean startupIntentProcessed = false;
    private static boolean allowScan = false;
    private Tag currentTag;

    public ReactNativeNFCModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);
        adapter = NfcAdapter.getDefaultAdapter(reactContext);
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {}

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent, false);
    }
    
    @Override
    public String getName() {
        return "ReactNativeNFC";
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param nfcAdapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter nfcAdapter) {
        
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
 
        final PendingIntent pendingIntent = PendingIntent.getActivity(
                activity.getApplicationContext(), 0, intent, 0);
 
        IntentFilter[] filters = new IntentFilter[3];

        String[] filterNames = new String[]{
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED
        };

        int pos = 0;

        for(String filter : filterNames){
            filters[pos] = new IntentFilter();
            filters[pos].addAction(filter);
            if(filter.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)){
                Log.d("NFC_PLUGIN_LOG", filter + " gets a mime type added to it");
                try {
                    filters[pos].addCategory(Intent.CATEGORY_DEFAULT);
                    filters[pos].addDataType("*/*");
                } catch (MalformedMimeTypeException e) {
                    Log.d("NFC_PLUGIN_LOG", "Check your mime type");
                    throw new RuntimeException("Check your mime type.");
                }
            }
            pos++;
        }
        
        try{
            Log.d("NFC_PLUGIN_LOG", "Starting Foreground Dispatch");
            nfcAdapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
        }catch(Exception e){
            Log.d("NFC_PLUGIN_LOG", "Failed enabling forground dispatch from permissions");
            Log.e("NFC_PLUGIN_LOG", e.toString());
        }
    }

    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        try{
            Log.d("NFC_PLUGIN_LOG", "stopForegroundDispatch called");
            adapter.disableForegroundDispatch(activity);
        }catch(Exception e){
            Log.d("NFC_PLUGIN_LOG", "Error in stopping forground dispatch");
            Log.d("NFC_PLUGIN_LOG", e.toString());
        }
    }

    private void handleIntent(Intent intent, boolean startupIntent) {
        Tag tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        currentTag = tag;

        if (intent != null && intent.getAction() != null) {
            Log.d("NFC_PLUGIN_LOG", "handling the intent");
            Log.d("ReactNative", "seen an action " + intent.getAction());
            String serialNumber = getSerialNumber(tag);
            switch (intent.getAction()){
                case NfcAdapter.ACTION_ADAPTER_STATE_CHANGED:
                    Log.d("NFC_PLUGIN_LOG", "ACTION_ADAPTER_STATE_CHANGED");
                    break;

                case NfcAdapter.ACTION_NDEF_DISCOVERED:
                   
                    Log.d("NFC_PLUGIN_LOG", "ACTION_NDEF_DISCOVERED");

                    final Ndef ndefTag = Ndef.get(tag);
                   

                    Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

                    
                    if (rawMessages != null) {
                        NdefMessage[] messages = new NdefMessage[rawMessages.length];
                        int pos = 0;
                        for (Parcelable row : rawMessages){
                            Log.d("NFC_PLUGIN_LOG:rawMsg", row.toString());
                            messages[pos] = (NdefMessage) row;
                            pos++;
                        }
                        processNdefMessages(serialNumber, messages, startupIntent);
                    }

                    final Handler timerHandler = new Handler();

                    final Runnable timerRunnable = new Runnable() {
                         @Override
                        public void run() {
                            try{
                                ndefTag.connect();
                                ndefTag.getNdefMessage();
                                ndefTag.close();
                                timerHandler.postDelayed(this, 500);
                            }catch(Exception err){
                                sendConnectionError();
                                Log.d("ReactNative", "*** tag connection error!!");
                            }
                        }
                    };
                    timerHandler.postDelayed(timerRunnable, 0);
                    break;

                case NfcAdapter.ACTION_TAG_DISCOVERED:
                    Log.d("NFC_PLUGIN_LOG", "ACTION_TAG_DISCOVERED");
                  
                    processTag(serialNumber, tag, startupIntent);
                    break;
                case NfcAdapter.ACTION_TECH_DISCOVERED:
                    Log.d("NFC_PLUGIN_LOG", "ACTION_TECH_DISCOVERED");
                   
                    processTag(serialNumber, tag, startupIntent);
                    break;
                default:
                    Log.d("NFC_PLUGIN_LOG:DEFAULT", intent.getAction());
            }
        }
    }

    /**
     * This method is used to retrieve the NFC data was acquired before the React Native App was loaded.
     * It should be called only once, when the first listener is attached.
     * Subsequent calls will return null;
     *
     * @param callback callback passed by javascript to retrieve the nfc data
     */
    @ReactMethod
    public void getStartUpNfcData(Callback callback){
        if(!startupNfcDataRetrieved){
            callback.invoke(DataUtils.cloneWritableMap(startupNfcData));
            startupNfcData = null;
            startupNfcDataRetrieved = true;
        } else {
            callback.invoke();
        }
    }

    @ReactMethod
    public void initialize() {
       
        if(adapter != null) {
            if (adapter.isEnabled()) {
                // Log.d("NFC_PLUGIN_LOG", "Reader should be started here, but not sure if that is how this works");
                setupForegroundDispatch(getCurrentActivity(), adapter);
                sendResponseEvent(EVENT_NFC_ENABLED, null);
            }else{
                sendResponseEvent(EVENT_NFC_MISSING, null);
            }
        }else{
            sendResponseEvent(EVENT_NFC_UNAVAILABLE, null);
        }
    }

    @ReactMethod
    public void isSupported(){
        if(adapter != null){
            if(adapter.isEnabled()){
                Log.d("NFC_PLUGIN_LOG", "EVENT_NFC_ENABLED THE THING IS ENABLED");
                sendResponseEvent(EVENT_NFC_ENABLED, null);
            }else{
                Log.d("NFC_PLUGIN_LOG", "EVENT_NFC_MISSING THE THING EXISTS BUT IS NOT ENABLED");
                sendResponseEvent(EVENT_NFC_MISSING, null);
            }
        }else{
            Log.d("NFC_PLUGIN_LOG", "EVENT_NFC_ENABLED THE THING IS SEEMS TO NOT HAVE THE THING");
            sendResponseEvent(EVENT_NFC_UNAVAILABLE, null);
        }
    }

    @ReactMethod
    public void writeTag(String message){
        Log.d("ReactNative", "writing rfid!");
        NdefMessage m = createTextMessage(message);
        _writeTag(m);
    }


    private void _writeTag(NdefMessage message) {
        if (currentTag != null) {
            try {
                Ndef ndefTag = Ndef.get(currentTag);
                if (ndefTag == null)  {
                // Let's try to format the Tag in NDEF
                    NdefFormatable nForm = NdefFormatable.get(currentTag);
                    
                    if (nForm != null) {
                        nForm.connect();
                        nForm.format(message);
                        nForm.close();
                    }
                }
                else {
                    int maxSize = ndefTag.getMaxSize();
                    int messageSize = message.getByteArrayLength();

                    if (maxSize >= messageSize){
                        ndefTag.connect();
                        Log.d("ReactNative", "AM WRITING NDEF MESSAGE!");
                        ndefTag.writeNdefMessage(message);
                        Log.d("ReactNative", "DONE WRITING NDEF MESSAGE!");
                        ndefTag.close();
                    }else{
                        Log.d("ReactNative", "Cannot write message, it is too big (" + messageSize + "). Maxsize is " + maxSize);
                    }
                   
                }
            }
            catch(Exception e) {
                 Log.d("ReactNative", "ERROR writing rfid!", e);
                e.printStackTrace();
            }
        }
    }

    private NdefMessage createTextMessage(String content) {
        try {
            // Get UTF-8 byte
            byte[] lang = Locale.getDefault().getLanguage().getBytes("UTF-8");
            byte[] text = content.getBytes("UTF-8"); // Content in UTF-8
        
            int langSize = lang.length;
            int textLength = text.length;
        
            ByteArrayOutputStream payload = new ByteArrayOutputStream(1 + textLength + langSize);
            payload.write((byte) (langSize & 0x1F));
            payload.write(lang, 0, langSize);
            payload.write(text, 0, textLength);
            NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT, new byte[0],
            payload.toByteArray());
         
            return new NdefMessage(new NdefRecord[]{record});
        }
        catch (Exception e) {
            Log.d("ReactNative", "Error creating text message " + e);
            e.printStackTrace();
        }
        
        return null;
    }

    private void sendConnectionError() {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(NFC_OUT_OF_RANGE, "");
    }

    private void sendEvent(@Nullable WritableMap payload) {
        payload.putString("origin", "android");
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(EVENT_NFC_DISCOVERED, payload);
    }

    private void sendResponseEvent(String event, @Nullable WritableMap payload) {
        if(payload != null){
            payload.putString("origin", "android");
        }
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(event, payload);
    }

    private String getSerialNumber(Tag tag){
        byte[] id = tag.getId();
        String serialNumber = DataUtils.bytesToHex(id);

        return serialNumber;
    }

    private void processNdefMessages(String serialNumber, NdefMessage[] messages, boolean startupIntent){
        NdefProcessingTask task = new NdefProcessingTask(serialNumber, startupIntent);
        task.execute(messages);
        stopForegroundDispatch(getActivity(), adapter);
    }

    private void processTag(String serialNumber, Tag tag, boolean startupIntent){
        TagProcessingTask task = new TagProcessingTask(serialNumber, startupIntent);
        task.execute(tag);
        stopForegroundDispatch(getActivity(), adapter);
    }

    @Override
    public void onHostResume() { }

    @Override
    public void onHostPause() {
        stopForegroundDispatch(getActivity(), adapter);
    }

    @Override
    public void onHostDestroy() {
        stopForegroundDispatch(getActivity(), adapter);
    }

    private Activity getActivity(){
        return getReactApplicationContext().getCurrentActivity();
    }

    private class NdefProcessingTask extends AsyncTask<NdefMessage[],Void,WritableMap> {

        private final String serialNumber;
        private final boolean startupIntent;

        NdefProcessingTask(String serialNumber, boolean startupIntent) {
            this.serialNumber = serialNumber;
            this.startupIntent = startupIntent;
        }

        @Override
        protected WritableMap doInBackground(NdefMessage[]... params) {
           
            NdefMessage[] messages = params[0];
            //Log.d("ReactNative", "seen an ndef message!!, writing nw");
            WritableMap s = NdefParser.parse(serialNumber, messages);
            //writeTag("07972639571");
          
            return s;
            //return null;
        }

        @Override
        protected void onPostExecute(WritableMap ndefData) {
            if(startupIntent) {
                startupNfcData = ndefData;
            }
            sendEvent(ndefData);
        }
    }


    private class TagProcessingTask extends AsyncTask<Tag, Void, WritableMap> {

        private final String serialNumber;
        private final boolean startupIntent;

        TagProcessingTask(String serialNumber, boolean startupIntent) {
            this.serialNumber = serialNumber;
            this.startupIntent = startupIntent;
        }

        @Override
        protected WritableMap doInBackground(Tag... params) {
            Tag tag = params[0];
            WritableMap map = TagParser.parse(serialNumber, tag);
            Log.d("NFC_PLUGIN_LOG", "" + tag.describeContents() + " <-> 0");
            
            int pos = 0;
            for(Tag param : params) {
                if (pos > 0) {
                    map.merge(TagParser.parse(serialNumber + pos, param));
                    Log.d("NFC_PLUGIN_LOG", "" + param.describeContents() + " <-> " + pos);
                }
                pos++;
            }

            //NdefMessage m = createTextMessage("07972639571");
            //writeTag(tag, m);
            return map;
        }

        @Override
        protected void onPostExecute(WritableMap tagData) {
            if(startupIntent) {
                startupNfcData = tagData;
            }
            sendEvent(tagData);
        }
    }
}
