package com.reactnativejitsimeet;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.jitsi.meet.sdk.BroadcastEvent;
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
