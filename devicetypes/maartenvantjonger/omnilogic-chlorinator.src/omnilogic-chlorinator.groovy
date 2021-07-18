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
    capability 'Health Check'
    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'level', 'number'
    attribute 'operatingState', 'number'
    attribute 'operatingMode', 'number'
    attribute 'status', 'number'
    attribute 'scMode', 'number'
    attribute 'instantSaltLevel', 'number'
    attribute 'avgSaltLevel', 'number'
    attribute 'chlrAlert', 'number'
    attribute 'chlrError', 'number'
    attribute 'isSuperChlorinator', 'number'
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
  sendEvent(name: 'isSuperChlorinator', value: attributes['isSuperChlorinator'], displayed: true)

  if (!getIsSuperChlorinator()) {
    sendEvent(name: 'level', value: 0, displayed: true)
  }
}

def refresh() {
	parent.logDebug('Executing Omnilogic Chlorinator refresh')
  parent.updateDeviceStatuses()
}

def ping() {
	parent.logDebug('Executing Omnilogic Chlorinator ping')
  refresh()
}

def parseStatus(deviceStatus, telemetryData) {
	parent.logDebug('Executing Omnilogic Chlorinator parseStatus')
	parent.logDebug(deviceStatus)

  def enabled = getIsSuperChlorinator() ? deviceStatus.@scMode.text() : deviceStatus.@enable.text()
  def onOff = enabled == '1' ? 'on' : 'off'
  sendEvent(name: 'switch', value: onOff, displayed: true)

  if (getIsSuperChlorinator()) {
    def level = enabled == '1' ? 100 : 0
    sendEvent(name: 'level', value: level, displayed: true)
  } else {
    def level = deviceStatus['@Timed-Percent'].text()
    sendEvent(name: 'level', value: level, displayed: true)
  }

  def operatingState = deviceStatus.@operatingState.text()
  sendEvent(name: 'operatingState', value: operatingState, displayed: true)

  def operatingMode = deviceStatus.@operatingMode.text()
  sendEvent(name: 'operatingMode', value: operatingMode, displayed: true)

  def status = deviceStatus.@status.text()
  sendEvent(name: 'status', value: status, displayed: true)

  def enable = deviceStatus.@enable.text()
  sendEvent(name: 'enable', value: enable, displayed: true)

  def scMode = deviceStatus.@scMode.text()
  sendEvent(name: 'scMode', value: scMode, displayed: true)

  def instantSaltLevel = deviceStatus.@instantSaltLevel.text()
  sendEvent(name: 'instantSaltLevel', value: instantSaltLevel, displayed: true)

  def avgSaltLevel = deviceStatus.@avgSaltLevel.text()
  sendEvent(name: 'avgSaltLevel', value: avgSaltLevel, displayed: true)

  def chlrAlert = deviceStatus.@chlrAlert.text()
  sendEvent(name: 'chlrAlert', value: chlrAlert, displayed: true)

  def chlrError = deviceStatus.@chlrError.text()
  sendEvent(name: 'chlrError', value: chlrError, displayed: true)
}

def on() {
  parent.logDebug('Executing on')

  if (getIsSuperChlorinator()) {
    enableSuperChlorinator(true)
  } else {
    enableChlorinator(true)
  }
}

def off() {
	parent.logDebug('Executing off')

  if (getIsSuperChlorinator()) {
    enableSuperChlorinator(false)
  } else {
    enableChlorinator(false)
  }
}

def setLevel(level) {
  parent.logDebug('Executing setLevel')

  setChlorinatorLevel(level)
}

def enableChlorinator(enable) {
  parent.logDebug("Executing enableChlorinator ${enable}")

  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'ChlorID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'Enabled', dataType: 'bool', value: enable]
  ]
  parent.performApiRequest('SetCHLOREnable', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      def onOff = enable ? 'on' : 'off'
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def setChlorinatorLevel(level) {
  parent.logDebug("Executing setChlorinatorLevel ${level}")

  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'ChlorID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'CfgState', dataType: 'byte', value: 3],
    [name: 'OpMode', dataType: 'byte', value: 1],
    [name: 'BOWType', dataType: 'byte', value: 1],
    [name: 'CellType', dataType: 'byte', value: 3],
    [name: 'TimedPercent', dataType: 'byte', value: level],
    [name: 'SCTimeout', dataType: 'byte', value: 24],
    [name: 'ORPTimout', dataType: 'byte', value: 24]
  ]
  parent.performApiRequest('SetCHLORParams', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      def onOff = enable ? 'on' : 'off'
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
      sendEvent(name: 'level', value: level, displayed: true, isStateChange: true)
    }
  }
}

def enableSuperChlorinator(enable) {
  parent.logDebug("Executing enableSuperChlorinator ${enable}")

  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'ChlorID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: enable ? 1 : 0]
  ]

  parent.performApiRequest('SetUISuperCHLORCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      def onOff = enable ? 'on' : 'off'
      def level = enable ? 100 : 0
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
      sendEvent(name: 'level', value: level, displayed: true, isStateChange: true)
    }
  }
}

def getIsSuperChlorinator() {
  return device.currentValue('isSuperChlorinator') == 1
}
