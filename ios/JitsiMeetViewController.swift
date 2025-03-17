import UIKit
import JitsiMeetSDK
import Foundation
import React

class JitsiMeetViewController: UIViewController {
  var conferenceOptions: JitsiMeetConferenceOptions?
  var resolver: RCTPromiseResolveBlock?
  var jitsiMeetView: JitsiMeetView?
  var videoMutedCount = true
  var conferenceActive = true
  var alertController: UIAlertController?
  let eventEmitter: EventEmitter = .shared!

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

  func conferenceTerminated(_ data: [AnyHashable : Any]!) {
      conferenceActive = false
      self.eventEmitter.sendEvent(withName: "onConferenceTerminated", body: nil)
      self.cleanUp()
      DispatchQueue.main.async {
        self.dismiss(animated: true)
    }
  }

  func customOverflowMenuButtonPressed(_ data: [AnyHashable: Any]!) {
      if let id = data["id"] as? String, id == "minimize" {
        self.dismiss(animated: true)
      }
  }

  
  private func checkCameraPermission(completion: @escaping (Bool) -> Void) {
      let authStatus = AVCaptureDevice.authorizationStatus(for: .video)
      
      switch authStatus {
      case .authorized:
          completion(true)
      case .denied, .restricted:
          completion(false)
      case .notDetermined:
          completion(true)
      @unknown default:
          completion(false)
      }
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

    DispatchQueue.main.asyncAfter(deadline: .now() + 0.50, execute: {
      self.present(self.alertController!, animated: true, completion: nil)
    })

  }
  
  func videoMutedChanged(_ data: [AnyHashable : Any]!) {
      guard let muted = data["muted"] as? Int else {
          print("Error: 'muted' key is not present or is not an Int.")
          return
      }

      if muted == 6 {
          conferenceActive = false
      }

      if muted == 0, conferenceActive, !videoMutedCount {
          checkCameraPermission { granted in
              if !granted {
                  self.showCameraPermissionDialog()
              }
          }
      }

      conferenceActive = true
      videoMutedCount = false
  }
}

@objc(EventEmitter)
class EventEmitter: RCTEventEmitter {

    public static var shared:EventEmitter?

    override init() {
        super.init()
        EventEmitter.shared = self
    }

    override func supportedEvents() -> [String]! {
        return [
           "onConferenceTerminated"
        ]
    }
}
