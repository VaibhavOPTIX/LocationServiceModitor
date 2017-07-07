### Android LocationServiceModitor 
[Executable file](https://github.com/VaibhavOPTIX/LocationServiceMonitor/blob/master/app-debug.apk)

This project will spawn a background service and then will listen for GPS location. The serviec will serup a location manager to listen to the location changes as well as setup the GnssStatus.Callback or GpsStatus.Listener to listen for SatelliteStatusChanged to get the number of satellite used in gettting the location fix.
The service will record the location when the service conforms to the validation applied. The service record this data to a text file on the device.

There are 2 record file :
1. Active mode files and 
2. Idle mode files. 
An enty to an Anctive mode file will happen after every 2 minutes given it is valid and the entry to Idel mode file will will happen every 30 mins if valid.

Along whith this the service also record the Battery health and the active Network information and writes then to a text file as well.



References: 

[ResultReceiver](https://guides.codepath.com/android/Starting-Background-Services#communicating-with-a-resultreceiver): Service
only needs to communicate with the activity or application that spawns it.Generic interface for receiving a callback result 
from someone. Use this by creating a subclass and implement onReceiveResult(int, Bundle), which you can then pass to others 
and send through IPC, and receive results they supply with send(int, Bundle).

[Services and Threading](https://guides.codepath.com/android/Managing-Threads-and-Custom-Services#executing-runnables-on-handlerthread):Thread management is important to understand because a custom service still runs in your application's main thread by default. If you create a custom Service, then you will still need to manage the background threads manually
[Why Using a HandlerThread is good](https://medium.com/@ali.muzaffar/handlerthreads-and-why-you-should-be-using-them-in-your-android-apps-dc8bf1540341)

### Installation & Development. 
1. clone this repo: `https://github.com/VaibhavOPTIX/LocationServiceMonitor.git`
2. run it on Android studio.
