# React Native NFC Reader
React Native NFC reader plugin enables iOS apps to read NFC tags encoded with NDEF Messages.

## Supported Platforms
+ iOS 11+
+ iPhone 7+

## Requirements
To enable your app to detect NFC tags, turn on the Near Field Communication Tag Reading capability in your Xcode project
and for your App ID [see here](http://help.apple.com/xcode/mac/current/#/dev88ff319e7)

## Installation
```bash
$ npm install @joeldiaz2302/react-native-rfid-nfc --save
```

## Configuration
### Plugin Configuration
+ Open up your project in xcode and right click the package.
+ Click Add files to 'Your project name'
+ Navigate to /node_modules/react-native-rfid-nfc/ios/
+ select all ReactNativeNFC** files and NFCHelper file
+ Click 'Add'
+ Click your project in the navigator on the left and go to build settings
+ Search for Objective-C Bridging Header
+ Double click on the empty column
+ Copy the full path to this file `ReactNativeNFC-Bridging-Header.h`
+ Enter the full path to `ReactNativeNFC-Bridging-Header.h` E.g. /Users/me/myApp/node_modules/@joeldiaz2302/react-native-rfid-nfc/ReactNativeNFC-Bridging-Header.h

### App Configuration
#### info.plist
add the following to info.plist
```xml
<key>NFCReaderUsageDescription</key>
<string>NFC NDEF Reading.</string>
```

#### YourAppName.entitlements
The following entry should be created automatically in your `YourAppName.entitlements` file once you enable NFC capability
in your app but if not add the following to `YourAppName.entitlements`
```xml
<dict>
	<key>com.apple.developer.nfc.readersession.formats</key>
	<array>
		<string>NDEF</string>
	</array>
</dict>
```

## Example
```js
// import NFCReader
import NFC from "@joeldiaz2302/react-native-rfid-nfc";

// initialize NFCReader Session
NFC.initialize()

// Listen on NFC Tag Scan Messages
NFC.addListener(name:String, (data) => {
  console.log(data)
})

// Remove the Listeners
NFC.removeListener(name:String)

// Remove all Listeners
NFC.removeAllListener()
```

## Methods
+ `initialize` - Creates NFC Reader Session
+ `addListener` - Add event listener to listen on NFC scan Messages
+ `removeListener` - Remove event Listeners

## Data
The returned data contains an array of NDEF Payload format

#### NDEF Payload format
Property | Values
--- | ---
format | The Type Name Format field of the payload, as defined by the NDEF specification.
type | The type of the payload, as defined by the NDEF specification.
identifier | The identifier of the payload, as defined by the NDEF specification.
payload | The data of the payload, as defined by the NDEF specification.