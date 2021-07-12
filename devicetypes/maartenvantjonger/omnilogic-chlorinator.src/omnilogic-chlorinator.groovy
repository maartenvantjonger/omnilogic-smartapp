/**
 *  Omnilogic Chlorinator
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: 'Omnilogic Chlorinator',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Switch Level'
    capability 'Actuator'
    capability 'Refresh'
    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'level', 'number'
  }

  tiles {
    standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

    controlTile('level', 'device.level', 'slider', range: '(1..100)', height: 2, width: 2, canChangeIcon: true, decoration: 'flat', inactiveLabel: false) {
      state 'level', action: 'setLevel'
    }

    main('switch')
    details(['switch', 'level'])
  }
}

def initialize(omnilogicId, attributes) {
	parent.logDebug('Executing Omnilogic Chlorinator initialize')

  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
  sendEvent(name: 'level', value: 0, displayed: true)
}

def refresh() {
	parent.logDebug('Executing Omnilogic Chlorinator refresh')
  parent.updateDeviceStatuses()
}

def parseStatus(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Chlorinator parseStatus')
	parent.logDebug(statusXmlNode)

  def enabled = statusXmlNode.@status.text() != "0"
  def level = statusXmlNode['@Timed-Percent'].text().toInteger()
  updateState(enabled, level)
}

def updateState(enabled, level) {
	parent.logDebug('Executing updateState')

  if (enabled != null) {
    def onOff = enabled ? 'on' : 'off'
    sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
  }

  if (level != null) {
    sendEvent(name: 'level', value: level, displayed: true)
  }
}

def on() {
  parent.logDebug('Executing on')
  setLevel(100)
}

def off() {
	parent.logDebug('Executing off')
  setLevel(0)
}

def setLevel(level) {
  parent.logDebug('Executing setLevel')
  setChlorinatorLevel(level)
}

def enableChlorinator(level) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'ChlorID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: level],
    [name: 'IsCountDownTimer', dataType: 'bool', value: false],
    [name: 'StartTimeHours', dataType: 'int', value: 0],
    [name: 'StartTimeMinutes', dataType: 'int', value: 0],
    [name: 'EndTimeHours', dataType: 'int', value: 0],
    [name: 'EndTimeMinutes', dataType: 'int', value: 0],
    [name: 'DaysActive', dataType: 'int', value: 0],
    [name: 'Recurring', dataType: 'bool', value: false]
  ]

  parent.performApiRequest('SetCHLOREnable', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      updateState(enable, level)
    }
  }
}

def enableSuperChlorinator(enable) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'ChlorID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: enable ? 100 : 0],
    [name: 'IsCountDownTimer', dataType: 'bool', value: false],
    [name: 'StartTimeHours', dataType: 'int', value: 0],
    [name: 'StartTimeMinutes', dataType: 'int', value: 0],
    [name: 'EndTimeHours', dataType: 'int', value: 0],
    [name: 'EndTimeMinutes', dataType: 'int', value: 0],
    [name: 'DaysActive', dataType: 'int', value: 0],
    [name: 'Recurring', dataType: 'bool', value: false]
  ]

  parent.performApiRequest('SetUISuperCHLORCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      updateState(enable, 100)
    }
  }
}
