/**
 *  Omnilogic Filter
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition(
    name: 'Omnilogic Filter',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Switch Level'
    capability 'Actuator'
    capability 'Refresh'

    if (getPlatform() == 'Hubitat') {
      capability 'Fan Control'
    } else {
      capability 'Fan Speed'
    }

    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
  }

  tiles {
    standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

		standardTile('fanSpeed', 'device.fanSpeed', width: 6, height: 4, canChangeIcon: true, decoration: 'flat') {
      state(0, label: 'off', action: 'switch.on', icon: 'st.thermostat.fan-off')
      state(1, label: 'low', action: 'switch.off', icon: 'st.thermostat.fan-on')
      state(2, label: 'medium', action: 'switch.off', icon: 'st.thermostat.fan-on')
      state(3, label: 'high', action: 'switch.off', icon: 'st.thermostat.fan-on')
		}

    main('switch')
    details(['switch', 'fanSpeed'])
  }
}

def initialize(omnilogicId, bowId) {
	parent.logDebug('Executing Omnilogic Filter initialize')
  sendEvent(name: 'omnilogicId', value: omnilogicId)
  sendEvent(name: 'bowId', value: bowId)
  sendEvent(name: 'level', value: 0)

  if (getPlatform() == 'Hubitat') {
    sendEvent(name: 'level', value: 0)
    sendEvent(name: 'fanSpeed', value: 'off')
    sendEvent(name: 'supportedFanSpeeds', value: ['off', 'low', 'medium', 'high'])
  } else {
    sendEvent(name: 'fanSpeed', value: 0)
  }
}

def refresh() {
	parent.logDebug('Executing Omnilogic Filter refresh')
  parent.updateDeviceStatuses()
}

def parseStatus(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Filter parseStatus')
	parent.logDebug(statusXmlNode)

  def filterSpeed = statusXmlNode?.@filterSpeed?.text().toInteger()
  updateState(filterSpeed)
}

def updateState(speed) {
	parent.logDebug('Executing Omnilogic Filter updateState')

  def onOff = speed == 0 ? 'off' : 'on'
  sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
  sendEvent(name: 'fanSpeed', value: speed, displayed: true)
  sendEvent(name: 'level', value: speed, displayed: true)
}

def on() {
  parent.logDebug('Executing Omnilogic Filter on')
  setPumpSpeed(100)
}

def off() {
	parent.logDebug('Executing Omnilogic Filter off')
  setPumpSpeed(0)
}

def setLevel(level) {
  parent.logDebug("Executing Omnilogic Filter setLevel ${level}")
  setPumpSpeed(level)
}

def setSpeed(speed) {
  parent.logDebug("Executing Omnilogic Filter setSpeed ${speed}")
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
  parent.logDebug("Executing Omnilogic Filter setFanSpeed ${speed}")
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
      updateState(speed)
    }
  }
}

def getPlatform() {
  physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat'
}
