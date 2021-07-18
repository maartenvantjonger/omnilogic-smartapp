/**
 *  Omnilogic Relay
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: 'Omnilogic Relay',
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
	parent.logDebug('Executing Omnilogic Relay initialize')

  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
}

def refresh() {
	parent.logDebug('Executing Omnilogic Relay refresh')
  parent.updateDeviceStatuses()
}

def ping() {
	parent.logDebug('Executing Omnilogic Relay ping')
  refresh()
}

def poll() {
  logDebug('Executing Omnilogic Relay poll')
  refresh()
}

def parseStatus(deviceStatus, telemetryData) {
	parent.logDebug('Executing Omnilogic Relay parseStatus')
	parent.logDebug(deviceStatus)

  def onOff = deviceStatus?.@relayState?.text() == '1' ? 'on' : 'off'
  sendEvent(name: 'switch', value: onOff, displayed: true)
}

def on() {
  parent.logDebug('Executing Omnilogic Relay on')
  setRelayState(true)
}

def off() {
  parent.logDebug('Executing Omnilogic Relay off')
  setRelayState(false)
}

def setRelayState(isOn) {
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
