/**
 *  Omnilogic Pump
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: 'Omnilogic Pump',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Switch'
    capability 'Actuator'
    capability 'Refresh'
    capability 'Health Check'
    capability 'Polling'
    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'pumpState', 'number'
    attribute 'pumpSpeed', 'number'
  }

  tiles {
    standardTile('switch', 'device.switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

    main('switch')
    details(['switch'])
  }
}

def initialize(omnilogicId, attributes) {
  logMethod('initialize', 'Arguments', [omnilogicId, attributes])
  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
}

def refresh() {
	logMethod('refresh')
  parent.updateDeviceStatuses()
}

def ping() {
	logMethod('ping')
  refresh()
}

def poll() {
	logMethod('poll')
  refresh()
}

def parseStatus(deviceStatus, telemetryData) {
  logMethod('parseStatus', 'Arguments', [deviceStatus])

  def pumpState = deviceStatus?.@pumpState?.text() ?: deviceStatus?.@filterState?.text()
  def onOff = pumpState == '1' ? 'on' : 'off'
  sendEvent(name: 'switch', value: onOff, displayed: true)

  def pumpSpeed = deviceStatus?.@pumpSpeed?.text().toInteger()
  sendEvent(name: 'pumpSpeed', value: pumpSpeed, displayed: true)

  def lastSpeed = deviceStatus?.@lastSpeed?.text().toInteger()
  sendEvent(name: 'lastSpeed', value: lastSpeed, displayed: true)
}

def on() {
	logMethod('on')
  setPumpState(true)
}

def off() {
	logMethod('off')
  setPumpState(false)
}

def setPumpState(isOn) {
	logMethod('setPumpState', 'Arguments', [isOn])

  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'EquipmentID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'IsOn', dataType: 'int', value: isOn ? 100 : 0],
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
      def onOff = isOn ? 'on' : 'off'
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
