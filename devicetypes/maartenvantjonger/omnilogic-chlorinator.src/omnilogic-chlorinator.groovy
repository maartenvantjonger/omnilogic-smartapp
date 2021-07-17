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

def ping() {
	parent.logDebug('Executing Omnilogic Chlorinator ping')
  refresh()
}

def parseStatus(deviceStatus, telemetryData) {
	parent.logDebug('Executing Omnilogic Chlorinator parseStatus')
	parent.logDebug(deviceStatus)

  def onOff = deviceStatus.@status.text() != '0' ? 'on' : 'off'
  sendEvent(name: 'switch', value: onOff, displayed: true)

  def level = deviceStatus['@Timed-Percent'].text()
  sendEvent(name: 'level', value: level, displayed: true)

  def status = deviceStatus.@status.text()
  sendEvent(name: 'status', value: status, displayed: true)

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

  def operatingMode = deviceStatus.@operatingMode.text()
  sendEvent(name: 'operatingMode', value: operatingMode, displayed: true)
}

def on() {
  parent.logDebug('Executing on')
  enableChlorinator(true)
}

def off() {
	parent.logDebug('Executing off')
  enableChlorinator(false)
}

def setLevel(level) {
  parent.logDebug('Executing setLevel')
  enableChlorinator(true)
}

def enableChlorinator(enable) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'ChlorID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'Enabled', dataType: 'bool', value: enable]
  ]
  parent.performApiRequest('SetCHLOREnable', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      def onOff = enable != 0 ? 'on' : 'off'
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
      //sendEvent(name: 'level', value: level, displayed: true, isStateChange: true)
    }
  }
}

def enableSuperChlorinator(enable) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'ChlorID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'Enabled', dataType: 'bool', value: enable]
  ]

  parent.performApiRequest('SetUISuperCHLORCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      def onOff = enable ? 'on' : 'off'
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
      //sendEvent(name: 'level', value: 150, displayed: true, isStateChange: true)
    }
  }
}
