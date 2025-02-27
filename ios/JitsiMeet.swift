import UIKit
import JitsiMeetSDK

@objc(JitsiMeet)
class JitsiMeet: NSObject {
    
  var vc: JitsiMeetViewController?
    
  @objc func hangUp() {
    self.vc?.jitsiMeetView.hangUp()
  }
    
  @objc func launchJitsiMeetView(_ options: NSDictionary, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
    DispatchQueue.main.async {
      let rootViewController = UIApplication.shared.delegate?.window??.rootViewController as! UIViewController
      let _vc = JitsiMeetViewController()

      _vc.resolver = resolve
      _vc.modalPresentationStyle = .fullScreen
      _vc.conferenceOptions = JitsiMeetUtil.buildConferenceOptions(options)
                
      rootViewController.present(_vc, animated: false)
        
        self.vc = _vc
    }
  }

  @objc func launch(_ options: NSDictionary, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
      DispatchQueue.main.async {
          if let activeVC = self.vc, activeVC.conferenceActive {
              if let topVC = UIApplication.shared.delegate?.window??.rootViewController {
                  if let presentedVC = topVC.presentedViewController {
                      presentedVC.dismiss(animated: false) {
                          topVC.present(activeVC, animated: true, completion: nil)
                      }
                  } else {
                      // Directly bring it to the front
                      topVC.present(activeVC, animated: true, completion: nil)
                  }
              }
              resolve(nil)
              return
          }

          return
      }
  }



  
  @objc
  static func requiresMainQueueSetup() -> Bool {
    return true
  }
}
