package com.reactnativejitsimeet;

import static com.reactnativejitsimeet.JitsiMeetModule.closeMuteDialog;
import static com.reactnativejitsimeet.JitsiMeetModule.resetMuteCount;
import static com.reactnativejitsimeet.JitsiMeetModule.resetMuteProps;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetView;


public class JitsiMeetActivityExtended extends JitsiMeetActivity {
  public static JitsiMeetActivityExtended instance;
    public static JitsiMeetActivityExtended getInstance() {
      return instance;
    }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    instance = this;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    resetMuteProps();
    closeMuteDialog();
    instance = null;
  }

  @Override
  public void onStart() {
    super.onStart();
    resetMuteCount();
  }


  @Override
  protected void onUserLeaveHint() {
    handlePictureInPicture();
  }

  public static void launchExtended(Context context, JitsiMeetConferenceOptions options) {
    Intent intent = new Intent(context, JitsiMeetActivityExtended.class);

    intent.setAction("org.jitsi.meet.CONFERENCE");
    intent.putExtra("JitsiMeetConferenceOptions", options);

    if (!(context instanceof Activity)) {
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    context.startActivity(intent);
  }

  public static void startPictureInPicture() {
    JitsiMeetActivityExtended activity = JitsiMeetActivityExtended.getInstance();
    if (activity != null) {
      JitsiMeetView view = activity.getJitsiView();
      if (view != null) {
        view.enterPictureInPicture();
      }
    }
  }

  private ReactNativeHost getReactNativeHost() {
    return ((ReactApplication) getApplication()).getReactNativeHost();
  }

  @Override
  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
    }

    ReactApplicationContext reactContext = (ReactApplicationContext) getReactNativeHost().getReactInstanceManager().getCurrentReactContext();
    if (reactContext != null) {
      WritableMap params = Arguments.createMap();
      params.putBoolean("isInPipMode", isInPictureInPictureMode);

      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit("onPiPModeChanged", params);
    }

  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    for (int i = 0; i < permissions.length; i++) {
      if (Manifest.permission.CAMERA.equals(permissions[i])) {
        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
          closeMuteDialog();
        }
      }
    }
  }

  private void handlePictureInPicture() {
    JitsiMeetConferenceOptions conferenceOptions = getIntent().getParcelableExtra("JitsiMeetConferenceOptions");

    if (conferenceOptions != null) {
      Bundle flags = conferenceOptions.getFeatureFlags();

      if (flags != null) {
        if (flags.getBoolean("pip.enabled")) {
          JitsiMeetView view = this.getJitsiView();

          if (view != null) {
            view.enterPictureInPicture();
          }
        }
      }
    }
  }
}
