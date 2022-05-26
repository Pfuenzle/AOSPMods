package sh.siava.AOSPMods.Utils;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Xml;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import javax.security.auth.callback.Callback;

import de.robv.android.xposed.XposedBridge;
import sh.siava.AOSPMods.BuildConfig;

public class NetworkStats {

    private static final long MB = 1024 * 1024;
    private static final long MINUTE = 60 * 1000L;
    private final Context mContext;
    private long lastUpdateTime = 0;
    private static final long refreshInterval = 10; //seconds
    private static final long saveThreshold = 10 * MB;
    private String statDataPath = "";
    private boolean enabled = false;
    private Calendar operationDate;

    private final ArrayList<networkStatCallback> callbacks = new ArrayList<>();
    private long totalRxBytes = 0;
    private long totalTxBytes = 0;
    private long todayCellRxBytes = 0, todayCellTxBytes = 0;
    private long cellRx = 0, cellTx = 0;
    private long totalCellRxBytes = 0, totalCellTxBytes = 0;

    private long todayRxBytes = 0, todayTxBytes = 0;

    private long rxData, txData;
    private long lastSaveTime;

    @SuppressWarnings("unused")
    public void registerCallback(networkStatCallback callback)
    {
        if(!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    @SuppressWarnings("unused")
    public void unRegisterCallback(networkStatCallback callback)
    {
        try {
            callbacks.remove(callback);
        } catch (Exception ignored){}
    }

    @SuppressWarnings("unused")
    public void resetCallbacks()
    {
        callbacks.clear();
    }

    private void resetStats() {
        operationDate = Calendar.getInstance();

        todayCellRxBytes
                = todayCellTxBytes
                = todayRxBytes
                = todayTxBytes
                = rxData
                = txData
                = 0;

        try {
            //noinspection ResultOfMethodCallIgnored
            new File(statDataPath).delete();
            //noinspection ResultOfMethodCallIgnored
            new File(statDataPath).mkdirs();
        }
        catch (Exception ignored){}
    }


    private final Handler mTrafficHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            long timeDelta = SystemClock.elapsedRealtime() - lastUpdateTime;

            if (timeDelta < refreshInterval * 1000L) {
                return;
            }
            lastUpdateTime = SystemClock.elapsedRealtime();

            // Calculate the data rate from the change in total bytes and time
            long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            long newTotalTxBytes = TrafficStats.getTotalTxBytes();
            long newCellTotalRxBytes = TrafficStats.getMobileRxBytes();
            long newCellTotalTxBytes = TrafficStats.getMobileTxBytes();

            rxData += newTotalRxBytes - totalRxBytes;
            txData += newTotalTxBytes - totalTxBytes;

            cellRx = newCellTotalRxBytes - totalCellRxBytes;
            cellTx = newCellTotalTxBytes - totalCellTxBytes;

            if (rxData > saveThreshold || txData > saveThreshold || (SystemClock.elapsedRealtime() - lastSaveTime) > (5*MINUTE)) {
                saveTrafficData();
            }

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            totalCellRxBytes = newCellTotalRxBytes;
            totalCellTxBytes = newCellTotalTxBytes;
            clearHandlerCallbacks();
            mTrafficHandler.postDelayed(mRunnable, refreshInterval * 1000L);
        }
    };

    BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            //noinspection deprecation
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                mTrafficHandler.sendEmptyMessage(0);
            }
        }
    };

    public void setStatus(boolean enabled)
    {
        if(enabled)
        {
            setEnabled();
        }
        else
        {
            setDisabled();
        }
        callbacks.forEach(callback -> callback.onStatChanged(this));

    }


    private void setEnabled()
    {
        if(enabled) return;

        try {
            //noinspection ResultOfMethodCallIgnored
            new File(statDataPath).mkdirs();
        }
        catch (Exception e)
        {
            return;
        }

        totalRxBytes = TrafficStats.getTotalRxBytes(); //if we're at startup so it's almost zero
        totalTxBytes = TrafficStats.getTotalTxBytes(); //if we're midway, then previous stats since boot worth nothing
                                                        //because we don't know those data are since when
        totalCellRxBytes = TrafficStats.getMobileRxBytes();
        totalCellTxBytes = TrafficStats.getMobileTxBytes();

        XposedBridge.log("try load");
        tryLoadData();
        XposedBridge.log("end load");

        IntentFilter filter = new IntentFilter();
        //noinspection deprecation
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        mContext.registerReceiver(mIntentReceiver, filter, null, null);
        operationDate = Calendar.getInstance();
        scheduleDateChange();
        enabled = true;
    }

    private void setDisabled()
    {
        if(!enabled) return;
        enabled = false;
        try {
            mContext.unregisterReceiver(mIntentReceiver);
            clearHandlerCallbacks();
        }catch (Exception e){}
    }

    private void scheduleDateChange()
    {
        try {
            Calendar nextDay = Calendar.getInstance();
            nextDay.set(Calendar.HOUR, 0);
            nextDay.set(Calendar.MINUTE, 0);
            nextDay.add(Calendar.DATE, 1);

            //noinspection ConstantConditions
            SystemUtils.AlarmManager().set(AlarmManager.RTC,
                    nextDay.getTimeInMillis(),
                    "",
                    () -> {
                        resetStats();
                        scheduleDateChange();
                    },
                    null);
        } catch (Throwable t)
        {
            if(BuildConfig.DEBUG)
            {
                XposedBridge.log("Error setting network reset schedule");
                t.printStackTrace();
            }
        }
    }

    private void saveTrafficData() {
        lastSaveTime = SystemClock.elapsedRealtime();
        todayRxBytes += rxData;
        todayTxBytes += txData;
        todayCellRxBytes += cellRx;
        todayCellTxBytes += cellTx;
        rxData = txData = 0;

        try {
            if(Calendar.getInstance().get(Calendar.DATE) != operationDate.get(Calendar.DATE)) //in a rare case that we didn't understand the date has changed, this will ensure
            {
                resetStats();
                return;
            }

            //inform callbacks
            callbacks.forEach(callback -> callback.onStatChanged(this));

            File dataFile = getDataFile();
            //noinspection ResultOfMethodCallIgnored
            dataFile.delete();

            FileWriter writer = new FileWriter(dataFile);
            XmlSerializer s = Xml.newSerializer();
            s.setOutput(writer);
            s.startDocument("UTF-8", true);

            s.startTag(null, "RXTotal");
            s.text(String.valueOf(todayRxBytes));
            s.endTag(null,"RXTotal");

            s.startTag(null, "TXTotal");
            s.text(String.valueOf(todayTxBytes));
            s.endTag(null,"TXTotal");

            s.startTag(null, "CellRX");
            s.text(String.valueOf(todayCellRxBytes));
            s.endTag(null,"CellRX");

            s.startTag(null, "CellTX");
            s.text(String.valueOf(todayCellTxBytes));
            s.endTag(null,"CellTX");

            s.endDocument();
            s.flush();

            writer.flush();
            writer.close();

        }
        catch (Throwable ignored){ignored.printStackTrace();}
    }

    private File getDataFile() {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat nameFormat = new SimpleDateFormat("yyyyMMdd");
        String filename = String.format("TrafficStats-%s.txt", nameFormat.format(Calendar.getInstance().getTime()));
        return new File(String.format("%s/%s", statDataPath, filename));
    }

    private final Runnable mRunnable = () -> mTrafficHandler.sendEmptyMessage(0);

    public NetworkStats(Context context)
    {
        mContext = context;

        statDataPath = mContext.getDataDir().getPath() + "/netStats";
    }

    public long getTodayDownloadBytes(boolean cellDataOnly)
    {
        return (cellDataOnly) ? todayCellRxBytes : todayRxBytes;
    }
    public long getTodayUploadBytes(boolean cellDataOnly)
    {
        return (cellDataOnly) ? todayCellTxBytes : todayTxBytes;
    }

    private void tryLoadData() {
        try {
            XmlPullParser p = Xml.newPullParser();

            FileReader reader = new FileReader(getDataFile());
            p.setInput(reader);
            p.getEventType()
            p.getAttributeValue(null, "RXTotal");
            File dataFile = getDataFile()
            todayRxBytes = json.getLong("RXTotal");
            todayTxBytes = json.getLong("TXTotal");
            todayCellRxBytes = json.getLong("CellRX");
            todayCellTxBytes = json.getLong("CellTX");

            XposedBridge.log("rx " + json.get("RXTotal"));
        }
        catch (Exception ignored){ignored.printStackTrace();}
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.removeMessages(0);
        mTrafficHandler.removeMessages(1);
    }

    public interface networkStatCallback extends Callback {
        void onStatChanged(NetworkStats stats);
    }
}