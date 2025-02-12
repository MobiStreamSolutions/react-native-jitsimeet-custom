import UIKit
import JitsiMeetSDK

class JitsiMeetViewController: UIViewController {
  var conferenceOptions: JitsiMeetConferenceOptions?
  var resolver: RCTPromiseResolveBlock?
  var jitsiMeetView = JitsiMeetView()
  var videoMutedCount = true
  var conferenceActive = true
  var alertController: UIAlertController?

  override func viewDidLoad() {
    super.viewDidLoad()
    
    let jitsiMeetView = JitsiMeetView()
    jitsiMeetView.delegate = self
    self.jitsiMeetView = jitsiMeetView

    jitsiMeetView.join(conferenceOptions)
    jitsiMeetView.delegate = self
      
    NotificationCenter.default.addObserver(self, selector: #selector(onOrientationChange), name: UIApplication.didChangeStatusBarOrientationNotification, object: nil)

    onOrientationChange()
    self.view.addSubview(jitsiMeetView)
  }
    
    @objc func onOrientationChange() {
        let isPortrait = UIApplication.shared.statusBarOrientation.isPortrait
        jitsiMeetView?.frame = CGRect.init(x: 0, y: isPortrait ? 44 : 0, width: self.view.frame.width, height: self.view.frame.height - ( isPortrait ? 78 : 10 ))
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        self.setNeedsStatusBarAppearanceUpdate()
    }
    
    fileprivate func cleanUp() {
      jitsiMeetView?.removeFromSuperview()
      jitsiMeetView = nil
    }
    
    override var prefersStatusBarHidden: Bool {
        return false
    }
}

extension JitsiMeetViewController: JitsiMeetViewDelegate {
  func ready(toClose data: [AnyHashable : Any]!) {
    if ((resolver) != nil) {
      resolver!([])
      resolver = nil
    }
  }
  
  fileprivate func cleanUp() {
    jitsiMeetView?.removeFromSuperview()
    jitsiMeetView = nil
    pipViewCoordinator = nil
  }
  
  func conferenceTerminated(_ data: [AnyHashable : Any]!) {
    conferenceActive = false
    self.alertController?.dismiss(animated: false, completion: {
      DispatchQueue.main.async {
        self.dismiss(animated: true)
      }
    })
    self.cleanUp()
   
  }
  
  private func checkCameraPermission() -> Bool {
    let authStatus = AVCaptureDevice.authorizationStatus(for: .video)
    return authStatus == .authorized
  }
  
  // Show alert for camera permission denial
  private func showCameraPermissionDialog() {
    if !conferenceActive {
      return;
    }
     alertController = UIAlertController(
      title: "ShadowHQ needs access to your camera",
      message: "Please go to settings and enable camera permissions for ShadowHQ",
      preferredStyle: .alert
    )
    
    alertController?.addAction(UIAlertAction(title: "Close", style: .default, handler: nil))
    alertController?.addAction(UIAlertAction(title: "Go to Settings", style: .default, handler: { _ in
      if let appSettings = URL(string: UIApplication.openSettingsURLString) {
        UIApplication.shared.open(appSettings, options: [:], completionHandler: nil)
      }
    }))

    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25, execute: {

      self.present(self.alertController!, animated: true, completion: nil)

    })

  }
  
  func videoMutedChanged(_ data: [AnyHashable : Any]!) {
    guard let muted = data["muted"] as? Int else {
      print("Error: 'muted' key is not present or is not an Int.")
      return
    }
    if (muted == 6)  {
      conferenceActive = false
    }
        // If video is unmuted, check camera permission
       if  muted == 0, conferenceActive, !checkCameraPermission(), !videoMutedCount {
          showCameraPermissionDialog()
        }
       conferenceActive = true
       videoMutedCount = false
    }
}
