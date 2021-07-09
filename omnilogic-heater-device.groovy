/**
 *  Omnilogic Heater
 *
 *  Copyright 2020 Maarten van Tjonger
 */
metadata {
  definition (
      name: 'Omnilogic Heater',
      namespace: 'maartenvantjonger',
      author: 'Maarten van Tjonger'
  ) {
    capability 'Thermostat Heating Setpoint'
    capability 'Switch'
    attribute 'poolId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'heatingSetpoint', 'number'
  }

  tiles {
    standardTile('switch', 'device,switch', width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
      state('off', label: '${name}', action: 'on')
      state('on', label: '${name}', action: 'off')
    }

    controlTile('heatingSetpoint', 'device.heatingSetpoint', 'slider', range: '(65..104)', height: 2, width: 2, canChangeIcon: true, decoration: 'flat', inactiveLabel: false) {
      state 'heatingSetpoint', action: 'setHeatingSetpoint'
    }

    main('switch')
    details(['switch'])
  }
}

def updateState(state) {
	parent.logDebug('Executing updateState')
  sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
  sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, displayed: true)
}

def on() {
  parent.logDebug('Executing on')
  sendEvent(name: 'switch', value: 'on', displayed: true, isStateChange: true)
  enableHeater(true)
}

def off() {
	parent.logDebug('Executing off')
  sendEvent(name: 'switch', value: 'off', displayed: true, isStateChange: true)
  enableHeater(false)
}

def setHeatingSetpoint(heatingSetpoint) {
  parent.logDebug('Executing setHeatingSetpoint')
  sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, displayed: true)
  setHeatingSetpoint(heatingSetpoint)
}

def enableHeater(enable) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('poolId')],
    [name: 'HeaterID', dataType: 'int', value: device.omnilogicId],
    [name: 'Version', dataType: 'string', value: 0],
    [name: 'Enabled', dataType: 'bool', value: enable]
  ]

  parent.performApiRequest('SetUIHeaterCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      sendEvent(name: 'switch', value: onOff, displayed: true, isStateChange: true)
    }
  }
}

def setHeatingSetpoint(omnilogicId, heatingSetpoint) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('poolId')],
    [name: 'HeaterID', dataType: 'int', value: device.omnilogicId],
    [name: 'Version', value: 0],
    [name: 'Temp', dataType: 'int', value: heatingSetpoint]
  ]

  parent.performApiRequest('SetUIHeaterCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, displayed: true)
    }
  }
}
