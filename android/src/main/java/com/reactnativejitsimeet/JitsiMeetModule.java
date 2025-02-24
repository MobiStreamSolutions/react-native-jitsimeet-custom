package com.reactnativejitsimeet;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.module.annotations.ReactModule;

import org.jitsi.meet.sdk.BroadcastAction;
import org.jitsi.meet.sdk.BroadcastEvent;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

@ReactModule(name = JitsiMeetModule.NAME)
public class JitsiMeetModule extends ReactContextBaseJavaModule {
  public static final String NAME = "JitsiMeet";

  private int toggleFirstVideoMuted = 0;

  private BroadcastReceiver onConferenceTerminatedReceiver;

  public JitsiMeetModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

//  @ReactMethod
//  public void hangUp() {
//    Intent hangUpBroadcastIntent = new Intent("org.jitsi.meet.HANG_UP");
//    LocalBroadcastManager.getInstance(getReactApplicationContext()).sendBroadcast(hangUpBroadcastIntent);
//  }

  @ReactMethod
  public void launchJitsiMeetView(ReadableMap options, Promise onConferenceTerminated) {
    JitsiMeetConferenceOptions.Builder builder = new JitsiMeetConferenceOptions.Builder();

    if (options.hasKey("room")) {
      builder.setRoom(options.getString("room"));
    } else {
      throw new RuntimeException("Room must not be empty");
    }

    try {
      builder.setServerURL(
        new URL(options.hasKey("serverUrl") ? options.getString("serverUrl") : "https://meet.jit.si"));
    } catch (MalformedURLException e) {
      throw new RuntimeException("Server url invalid");
    }

    if (options.hasKey("userInfo")) {
      ReadableMap userInfoMap = options.getMap("userInfo");

      if (userInfoMap != null) {
        JitsiMeetUserInfo userInfo = new JitsiMeetUserInfo();

        if (userInfoMap.hasKey("displayName")) {
          userInfo.setDisplayName(userInfoMap.getString("displayName"));
        }

        if (userInfoMap.hasKey("email")) {
          userInfo.setEmail(userInfoMap.getString("email"));
        }

        if (userInfoMap.hasKey("avatar")) {
          try {
            userInfo.setAvatar(new URL(userInfoMap.getString("avatar")));
          } catch (MalformedURLException e) {
            throw new RuntimeException("Avatar url invalid");
          }
        }

        builder.setUserInfo(userInfo);
      }
    }

    if (options.hasKey("token")) {
      builder.setToken(options.getString("token"));
    }

    // Set built-in config overrides
    if (options.hasKey("subject")) {
      builder.setSubject(options.getString("subject"));
    }

    if (options.hasKey("audioOnly")) {
      builder.setAudioOnly(options.getBoolean("audioOnly"));
    }

    if (options.hasKey("audioMuted")) {
      builder.setAudioMuted(options.getBoolean("audioMuted"));
    }

    if (options.hasKey("videoMuted")) {
      builder.setVideoMuted(options.getBoolean("videoMuted"));
    }

    // Set the feature flags
    if (options.hasKey("featureFlags")) {
      ReadableMap featureFlags = options.getMap("featureFlags");
      ReadableMapKeySetIterator iterator = featureFlags.keySetIterator();
      while (iterator.hasNextKey()) {
        String flag = iterator.nextKey();
        Boolean value = featureFlags.getBoolean(flag);
        builder.setFeatureFlag(flag, value);
      }
    }

    // Set the config overrides
    if (options.hasKey("configOverrides")) {
      ReadableMap configOverrides = options.getMap("configOverrides");
      ReadableMapKeySetIterator iterator = configOverrides.keySetIterator();
      while (iterator.hasNextKey()) {
        String flag = iterator.nextKey();
        Boolean value = configOverrides.getBoolean(flag);
        builder.setConfigOverride(flag, value);
      }
    }

    JitsiMeetActivityExtended.launchExtended(getReactApplicationContext(), builder.build());

    this.registerOnConferenceTerminatedListener(onConferenceTerminated);
    this.registerForBroadcastMessages();
  }

  @ReactMethod
  public void launch(ReadableMap options, Promise onConferenceTerminated) {
    launchJitsiMeetView(options, onConferenceTerminated);
  }

  private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      onBroadcastReceived(context, intent);
    }
  };

  private void registerForBroadcastMessages() {
    IntentFilter intentFilter = new IntentFilter();

        /* This registers for every possible event sent from JitsiMeetSDK
           If only some of the events are needed, the for loop can be replaced
           with individual statements:
           ex:  intentFilter.addAction(BroadcastEvent.Type.AUDIO_MUTED_CHANGED.getAction());
                intentFilter.addAction(BroadcastEvent.Type.CONFERENCE_TERMINATED.getAction());
                ... other events
         */


    for (BroadcastEvent.Type type : BroadcastEvent.Type.values()) {
      intentFilter.addAction(type.getAction());
    }

    LocalBroadcastManager.getInstance(getReactApplicationContext()).registerReceiver(broadcastReceiver, intentFilter);
  }
  private void onBroadcastReceived(Context context, Intent intent) {
    if (intent != null) {
      BroadcastEvent event = new BroadcastEvent(intent);
      if (event.getType() == BroadcastEvent.Type.VIDEO_MUTED_CHANGED) {
        Map<String, Object> data = event.getData();
        boolean isMuted = false;
        if (data.containsKey("muted") && data.get("muted") instanceof Boolean) {
          isMuted = (Boolean) data.get("muted");
        }

        if (!isMuted && toggleFirstVideoMuted >= 2 && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
          != PackageManager.PERMISSION_GRANTED) {
          JitsiMeetActivityExtended jitsiActivity = JitsiMeetActivityExtended.getInstance();
          if (jitsiActivity != null) {
            showMuteDialog();
          }
        }
        toggleFirstVideoMuted++;
      }
    }
  }

  private void showMuteDialog() {
    JitsiMeetActivityExtended jitsiActivity = JitsiMeetActivityExtended.getInstance();
    jitsiActivity.runOnUiThread(() -> {
      new AlertDialog.Builder(jitsiActivity)
        .setTitle("ShadowHQ needs your camera permission")
        .setMessage("Please go to your device settings to enable video calling")
        .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
        .setNegativeButton("Go to Settings", (dialog, which) -> {
          // Open the app's settings
          dialog.dismiss();
          Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
          Uri uri = Uri.fromParts("package", jitsiActivity.getPackageName(), null);
          intent.setData(uri);
          jitsiActivity.startActivity(intent);
        })
        .show();
    });
  }


  private void registerOnConferenceTerminatedListener(Promise onConferenceTerminated) {
    onConferenceTerminatedReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        BroadcastEvent event = new BroadcastEvent(intent);

        onConferenceTerminated.resolve(null);

        LocalBroadcastManager.getInstance(getReactApplicationContext()).unregisterReceiver(onConferenceTerminatedReceiver);
      }
    };

    IntentFilter intentFilter = new IntentFilter(BroadcastEvent.Type.CONFERENCE_TERMINATED.getAction());
    toggleFirstVideoMuted = 0;
    LocalBroadcastManager.getInstance(getReactApplicationContext()).registerReceiver(this.onConferenceTerminatedReceiver, intentFilter);
  }
}
