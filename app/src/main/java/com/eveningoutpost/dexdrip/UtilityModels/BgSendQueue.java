package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.util.Log;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.Models.BgReading;

import java.util.List;

/**
 * Created by stephenblack on 11/7/14.
 */
@Table(name = "BgSendQueue", id = BaseColumns._ID)
public class BgSendQueue extends Model {

    @Column(name = "bgReading", index = true)
    public BgReading bgReading;

    @Column(name = "success", index = true)
    public boolean success;

    @Column(name = "mongo_success", index = true)
    public boolean mongo_success;

    @Column(name = "operation_type")
    public String operation_type;

    public static BgSendQueue nextBgJob() {
        return new Select()
                .from(BgSendQueue.class)
                .where("success = ?", false)
                .orderBy("_ID desc")
                .limit(1)
                .executeSingle();
    }

    public static List<BgSendQueue> queue() {
        return new Select()
                .from(BgSendQueue.class)
                .where("success = ?", false)
                .orderBy("_ID asc")
                .limit(20)
                .execute();
    }
    public static List<BgSendQueue> mongoQueue() {
        return new Select()
                .from(BgSendQueue.class)
                .where("mongo_success = ?", false)
                .where("operation_type = ?", "create")
                .orderBy("_ID asc")
                .limit(10)
                .execute();
    }

    public static void addToQueue(BgReading bgReading, String operation_type, Context context) {
        BgSendQueue bgSendQueue = new BgSendQueue();
        bgSendQueue.operation_type = operation_type;
        bgSendQueue.bgReading = bgReading;
        bgSendQueue.success = false;
        bgSendQueue.mongo_success = false;

        bgSendQueue.save();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (prefs.getBoolean("cloud_storage_mongodb_enable", false) || prefs.getBoolean("cloud_storage_api_enable", false)) {
            Log.w("SENSOR QUEUE:", String.valueOf(bgSendQueue.mongo_success));
            if (operation_type == "create") {
                MongoSendTask task = new MongoSendTask(context, bgSendQueue);
                task.execute();
            }
        }

        if(prefs.getBoolean("broadcast_data_through_intents", false)) {
            Log.i("SENSOR QUEUE:", "Broadcast data");
            final Bundle bundle = new Bundle();
            bundle.putDouble(Intents.EXTRA_BG_ESTIMATE, bgReading.calculated_value);
            bundle.putDouble(Intents.EXTRA_BG_SLOPE, bgReading.calculated_value_slope);
            bundle.putString(Intents.EXTRA_BG_SLOPE_NAME, bgReading.slopeName());
            bundle.putInt(Intents.EXTRA_SENSOR_BATTERY, bgReading.sensor.latest_battery_level);
            bundle.putLong(Intents.EXTRA_TIMESTAMP, bgReading.timestamp);

            Intent intent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE);
            intent.putExtras(bundle);
            context.sendBroadcast(intent, Intents.RECEIVER_PERMISSION);
        }
    }

    public void markMongoSuccess() {
        mongo_success = true;
        save();
    }
}
