# Hayward Omnilogic smartapp for use with Samsung Smartthings and Hubitat

## Introduction

With only a few weeks left in the pool season, I've completed the first release of my integration for Hayward OmniLogic pool controllers.
The main goal of the integration is to be able to control pool equipment using Google Assistant and the Google Home app. Hayward only has a very basic and hard to use built-in integration with Google Assistant. With this smartapp you can control your pool equipment from your pool or spa, without having to touch anything with your wet hands (provided you have a Google Assistant enabled phone or speaker nearby).
Of course you'll also be able to control the equipment using your hub's app or website if you prefer.


## Supported devices

- Variable Speed Pump
- Pump
- Heater
- Chlorinator
- Super Chlorinator
- Light (Regular or ColorLogic)
- Relay
- Temperature Sensor


## Examples of things you can say

Depending on device names:

- *Hey Google, turn on Pool Filter*
- *Hey Google, set Pool Filter to 90%*
- *Hey Google, what is the Pool temperature?*
- *Hey Google, set Spa Heater to heat*
- *Hey Google, set Spa Heater to 98 degrees*
- *Hey Google, what is the Spa Heater temperature?*
- *Hey Google, set Chlorinator to 50%*
- *Hey Google, turn on Super Chlorinator*
- *Hey Google, what is the Pool temperature?*
- *Hey Google, turn on Pool Cleaner*
- *Hey Google, turn on Spa Spillover*

Note: Light and Relay devices have not been tested. I put my pool light on a zigbee switch last year so I haven't been able to try it out. If you use either of these devices, please let me know if they work.
If haven't tested this with Amazon Echo devices since I don't own any, but I'm curious to hear what works and what doesn't.


## Installation

The integration consists of a Smartapp to handle communication with the Hayward OmniLogic API and 8 device handlers to control individual devices.

Hubitat installation bundle: https://github.com/maartenvantjonger/omnilogic-smartapp/releases/download/1.0/omnilogic-v1.0.zip
Source code: https://github.com/maartenvantjonger/omnilogic-smartapp

In Hubitat, go to *Bundles -> Import ZIP*, and import **omnilogic-v1.0.zip**

In Smartthings:
- Log in to https://consigliere-regional.api.smartthings.com
- Go to *My SmartApps* and click *New SmartApp*
- Click the *From Code* tab and copy/paste the contents of the OmniLogic smartapp: https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/smartapps/maartenvantjonger/omnilogic.src/omnilogic.groovy
- Click *Create* and then *Publish -> For Me*
- Go to *My Device Handlers* and click *Create New Device Handler* and perform the same steps for all device handlers:
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-vsp.src/omnilogic-vsp.groovy
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-temperature-sensor.src/omnilogic-temperature-sensor.groovy
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-super-chlorinator.src/omnilogic-super-chlorinator.groovy
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-relay.src/omnilogic-relay.groovy
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-pump.src/omnilogic-pump.groovy
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-light.src/omnilogic-light.groovy
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-heater.src/omnilogic-heater.groovy
- https://raw.githubusercontent.com/maartenvantjonger/omnilogic-smartapp/main/devicetypes/maartenvantjonger/omnilogic-chlorinator.src/omnilogic-chlorinator.groovy


## Configuration

- After installation you can add the OmniLogic smartapp and fill in your OmniLogic username and password. The MSP ID can be found in your OmnoLogic display or in the OmniLogic app in the *About* popup.

- Go to *Devices* in the OmniLogic smartapp. The app gathers your OmniLogic configuration and will show you a list of available devices to choose from. Select the ones you would like to control, and they will be added to your hub as individual devices.

- Make sure Google Home integration is enabled for you hub, and synchronize the devices you've just created.


## Known issues

- The smartapp updates device statuses every 15 minutes. During those 15 minutes, device on/off statuses may show incorrectly in case you have your pool equipment on a schedule or if you're using the Hayward app or website to control the equipment on the side.

- In the Google Home UI, thermostats cannot be set beyond 90 degrees Fahrenheit. However, you can still say *Hey Google, set Spa Heater to 104 degrees*
Another workaround is to set the temperature in the Hub's app or website or in the Hayward OmniLogic app, and only using Google Home to turn the heater on and off.

#### In Hubitat

There are some limitations and quirks with Hubitat's Google Home integration, but all issues below can be worked around by using Hubitat's Google Home Community smartapp instead of Hubitat's built-in Google Home smartapp.

- Variable Speed Pumps and chrorinators will be exposed to Google Home as lights instead of *Switch Level*s.

- Heaters are exposed to Google Home as Thermostats that can cool or heat/cool, which is not true.

- Temperature sensors are not exposed to Google Home. Aside from the Google Home Community workaround, water temperature is also available through Heater thermostat(s). Say *Hey Google, what is the Spa Heater temperature?*


## Notes

I hope this makes a few of you happy and I'm open to feedback and suggestions.
For developers: All pull requests into the develop branch of the git repository will be considered.
https://github.com/maartenvantjonger/omnilogic-smartapp
