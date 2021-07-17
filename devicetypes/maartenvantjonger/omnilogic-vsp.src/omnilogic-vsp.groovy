/**
 *  Omnilogic VSP (Variable Speed Pump)
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition(
    name: 'Omnilogic VSP',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Switch Level'
    capability 'Actuator'
    capability 'Refresh'
    capability 'Health Check'

    if (getPlatform() == 'Hubitat') {
      capability 'Fan Control'
    } else {
      capability 'Fan Speed'
    }

    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'lastSpeed', 'number'
    attribute 'valvePosition', 'number'
    attribute 'whyFilterIsOn', 'number'
    attribute 'fpOverride ', 'number'
  }

  tiles {
    standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

		standardTile('fanSpeed', 'device.fanSpeed', width: 6, height: 4, canChangeIcon: true, decoration: 'flat') {
      state('0', label: 'off', action: 'switch.on', icon: 'st.thermostat.fan-off')
      state('1', label: 'low', action: 'switch.off', icon: 'st.thermostat.fan-on')
      state('2', label: 'medium', action: 'switch.off', icon: 'st.thermostat.fan-on')
      state('3', label: 'high', action: 'switch.off', icon: 'st.thermostat.fan-on')
		}

    main('switch')
    details(['switch', 'fanSpeed'])
  }
}

def initialize(omnilogicId, attributes) {
	parent.logDebug('Executing Omnilogic VSP initialize')

  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
  sendEvent(name: 'level', value: 0, displayed: true)

  if (getPlatform() == 'Hubitat') {
    sendEvent(name: 'level', value: 0, displayed: true)
    sendEvent(name: 'fanSpeed', value: 'off', displayed: true)
    sendEvent(name: 'supportedFanSpeeds', value: ['off', 'low', 'medium', 'high'], displayed: true)
  } else {
    sendEvent(name: 'fanSpeed', value: 0, displayed: true)
  }
}

def refresh() {
	parent.logDebug('Executing Omnilogic VSP refresh')
  parent.updateDeviceStatuses()
}

def ping() {
	parent.logDebug('Executing Omnilogic VSP ping')
  refresh()
}

def parseStatus(deviceStatus, telemetryData) {
	parent.logDebug('Executing Omnilogic VSP parseStatus')
	parent.logDebug(deviceStatus)

  def onOff = deviceStatus?.@filterState?.text() == '1' ? 'on' : 'off'
  sendEvent(name: 'switch', value: onOff, displayed: true)

  def level = deviceStatus?.@filterSpeed?.text().toInteger()
  sendEvent(name: 'level', value: level, displayed: true)

  def lastSpeed = deviceStatus?.@lastSpeed?.text().toInteger()
  sendEvent(name: 'lastSpeed', value: lastSpeed, displayed: true)

  def valvePosition = deviceStatus?.@valvePosition?.text().toInteger()
  sendEvent(name: 'valvePosition', value: valvePosition, displayed: true)

  def whyFilterIsOn = deviceStatus?.@whyFilterIsOn?.text().toInteger()
  sendEvent(name: 'whyFilterIsOn', value: whyFilterIsOn, displayed: true)

  def fpOverride = deviceStatus?.@fpOverride?.text().toInteger()
  sendEvent(name: 'fpOverride', value: fpOverride, displayed: true)
}

def on() {
  parent.logDebug('Executing Omnilogic VSP on')

  def lastSpeed = device.currentValue('lastSpeed')?.toInteger()
  setPumpSpeed(lastSpeed > 0 ? lastSpeed : 100)
}

def off() {
	parent.logDebug('Executing Omnilogic VSP off')
  setPumpSpeed(0)
}

def setLevel(level) {
  parent.logDebug("Executing Omnilogic VSP setLevel ${level}")
  setPumpSpeed(level)
}

def setSpeed(speed) {
  parent.logDebug("Executing Omnilogic VSP setSpeed ${speed}")
  sendEvent(name: 'fanSpeed', value: speed, displayed: true)

  switch (speed) {
    case 'off':
      setPumpSpeed(0)
      break
    case 'low':
      setPumpSpeed(75)
      break
    case 'medium':
      setPumpSpeed(85)
      break
    case 'high':
      setPumpSpeed(100)
      break
    default:
      break
  }
}

def setFanSpeed(speed) {
  parent.logDebug("Executing Omnilogic VSP setFanSpeed ${speed}")
  sendEvent(name: 'fanSpeed', value: speed, displayed: true)

  switch (speed as Integer) {
    case 0:
      setPumpSpeed(0)
      break
    case 1:
      setPumpSpeed(75)
      break
    case 2:
      setPumpSpeed(90)
      break
    case 3:
      setPumpSpeed(100)
      break
    case 4:
      setPumpSpeed(100)
      break
    default:
     break
  }
}

def setPumpSpeed(speed) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'EquipmentID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: speed],
    [name: 'IsCountDownTimer', dataType: 'bool', value: false],
    [name: 'StartTimeHours', dataType: 'int', value: 0],
    [name: 'StartTimeMinutes', dataType: 'int', value: 0],
    [name: 'EndTimeHours', dataType: 'int', value: 0],
    [name: 'EndTimeMinutes', dataType: 'int', value: 0],
    [name: 'DaysActive', dataType: 'int', value: 0],
    [name: 'Recurring', dataType: 'bool', value: false]
  ]

  parent.performApiRequest('SetUIEquipmentCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      def onOff = speed == 0 ? 'off' : 'on'
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
      sendEvent(name: 'level', value: speed, displayed: true, isStateChange: true)
    }
  }
}

def getPlatform() {
  physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat'
}
