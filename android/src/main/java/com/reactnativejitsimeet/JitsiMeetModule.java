package com.reactnativejitsimeet;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.Rational;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.jitsi.meet.sdk.BroadcastAction;
import org.jitsi.meet.sdk.BroadcastEvent;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

@ReactModule(name = JitsiMeetModule.NAME)
public class JitsiMeetModule extends ReactContextBaseJavaModule {
  public static final String NAME = "JitsiMeet";

  public static boolean activityStart = false;

  private static int toggleFirstVideoMuted = 0;

  private BroadcastReceiver onConferenceTerminatedReceiver;

  private boolean isPermissionRequestShowing = false;

  private static AlertDialog muteDialog;

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

    ArrayList<Bundle> customToolbarButtons = new ArrayList<Bundle>();

    Bundle firstCustomButton = new Bundle();

    firstCustomButton.putString("text", "Minimize call");
    firstCustomButton.putString("icon", "android.resource://" + getReactApplicationContext().getPackageName() + "/drawable/minus");
    firstCustomButton.putString("id", "minimize");

    customToolbarButtons.add(firstCustomButton);
    builder.setConfigOverride("customToolbarButtons", customToolbarButtons);
    JitsiMeetActivityExtended.launchExtended(getReactApplicationContext(), builder.build());

    this.registerOnConferenceTerminatedListener(onConferenceTerminated);
    this.registerForBroadcastMessages();
  }

  @ReactMethod
  public void launch(ReadableMap options, Promise onConferenceTerminated) {
    launchJitsiMeetView(options, onConferenceTerminated);
  }

  @ReactMethod
  public void resumeJitsiCall(Promise promise) {
    JitsiMeetActivityExtended jitsiActivity = JitsiMeetActivityExtended.getInstance();

    if (jitsiActivity != null) {
      Intent intent = new Intent(jitsiActivity, JitsiMeetActivityExtended.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      jitsiActivity.startActivity(intent);
      promise.resolve(true);
    } else {
      // No active Jitsi call
      promise.resolve(false);
    }
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

      if (event.getType() == BroadcastEvent.Type.CUSTOM_OVERFLOW_MENU_BUTTON_PRESSED) {
        Map<String, Object> data = event.getData();
        if ("minimize".equals(data.get("id"))) {
          Rational aspectRatio = new Rational(16, 9);

          // Create PictureInPictureParams.Builder and configure
          PictureInPictureParams.Builder pipBuilder = null;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            pipBuilder = new PictureInPictureParams.Builder();

            pipBuilder.setAspectRatio(aspectRatio);
            JitsiMeetActivityExtended jitsiActivity = JitsiMeetActivityExtended.getInstance();
            // Enter PiP mode
            toggleFirstVideoMuted = 0;
            JitsiMeetActivityExtended.startPictureInPicture();
          }
        }
      }

      if (event.getType() == BroadcastEvent.Type.CONFERENCE_TERMINATED) {
        getReactApplicationContext()
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
          .emit("onConferenceTerminated", null);
      }
    }
  }

  private void showMuteDialog() {
    JitsiMeetActivityExtended jitsiActivity = JitsiMeetActivityExtended.getInstance();
    if (jitsiActivity == null || jitsiActivity.isFinishing() || jitsiActivity.isDestroyed()) {
      return;
    }

    jitsiActivity.runOnUiThread(() -> {
      if (muteDialog != null && muteDialog.isShowing()) {
        return;
      }

      muteDialog = null;

      muteDialog = new AlertDialog.Builder(jitsiActivity)
        .setTitle("ShadowHQ needs your camera permission")
        .setMessage("Please go to your device settings to enable video calling")
        .setPositiveButton("Close", (dialog, which) -> {
          muteDialog = null;
          dialog.dismiss();
        })
        .setNegativeButton("Go to Settings", (dialog, which) -> {
          Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
          Uri uri = Uri.fromParts("package", jitsiActivity.getPackageName(), null);
          intent.setData(uri);
          jitsiActivity.startActivity(intent);
          muteDialog = null;
          dialog.dismiss();
        })
        .setOnCancelListener(dialog -> muteDialog = null)
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
  @ReactMethod
  public void addListener(String eventName) {}

  @ReactMethod
  public void removeListeners(Integer count) {}

  public static void closeMuteDialog() {
    if (muteDialog != null && muteDialog.isShowing()) {
      muteDialog.dismiss();
      muteDialog = null;
    }
  }
  public static void resetMuteCount() {
    if (!activityStart) {
      activityStart = true;
      return;
    }
    toggleFirstVideoMuted = 1;
    closeMuteDialog();
  }
  public static void resetMuteProps() {
    toggleFirstVideoMuted = 0;
    activityStart = false;
  }

}
