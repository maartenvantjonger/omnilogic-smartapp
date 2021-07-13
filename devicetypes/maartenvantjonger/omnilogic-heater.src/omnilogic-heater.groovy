/**
 *  Omnilogic Heater
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: 'Omnilogic Heater',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability "Actuator"
    capability "Refresh"
    capability "Sensor"
    capability "Temperature Measurement"
    capability "Thermostat"
    capability "Thermostat Heating Setpoint"
    capability "Thermostat Mode"
    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'min', 'number'
    attribute 'max', 'number'
    attribute 'minimum', 'number'
    attribute 'maximum', 'number'
  }

  tiles {
    controlTile('heatingSetpoint', 'device.heatingSetpoint', 'slider', range: '(65..104)', height: 2, width: 2, canChangeIcon: true, decoration: 'flat', inactiveLabel: false) {
      state 'heatingSetpoint', action: 'setHeatingSetpoint'
    }

    multiAttributeTile(name: 'thermostatFull', type: 'thermostat', width: 6, height: 4) {
      tileAttribute('device.temperature', key: 'PRIMARY_CONTROL') {
        attributeState('temp', label:'${currentValue}', unit: 'F', defaultState: true)
      }
      tileAttribute('device.thermostatMode', key: 'THERMOSTAT_MODE') {
        attributeState('off', label: '${name}')
        attributeState('heat', label: '${name}')
      }
      tileAttribute('device.heatingSetpoint', key: 'HEATING_SETPOINT') {
        attributeState('heatingSetpoint', label: '${currentValue}', unit: 'F', defaultState: true)
      }
    }

    main('heatingSetpoint')
		details(['heatingSetpoint', 'thermostatFull'])
  }
}

def initialize(omnilogicId, attributes) {
	parent.logDebug('Executing Omnilogic Heater initialize')

  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
  sendEvent(name: 'supportedThermostatModes', value: ['off', 'heat'], displayed: true)
  sendEvent(name: 'thermostatMode', value: 'off', displayed: true)
  sendEvent(name: 'temperature', value: 0, unit: 'F', displayed: true)
  sendEvent(name: 'unit', value: 'F', displayed: true)
  sendEvent(name: 'minimum', value: 65, unit: 'F', displayed: true)
  sendEvent(name: 'maximum', value: 104, unit: 'F', displayed: true)
  sendEvent(name: 'min', value: 65, unit: 'F', displayed: true)
  sendEvent(name: 'max', value: 104, unit: 'F', displayed: true)
}

def refresh() {
	parent.logDebug('Executing Omnilogic Heater refresh')
  parent.updateDeviceStatuses()
}

def parseStatus(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Heater parseStatus')
	parent.logDebug(statusXmlNode)

  def enabled = statusXmlNode.@enable.text() == 'yes'
  def heatingSetpoint = statusXmlNode['@Current-Set-Point'].text().toInteger()
  updateState(enabled, heatingSetpoint)
}

def updateState(enabled, heatingSetpoint) {
  parent.logDebug("Executing Omnilogic Heater updateState enabled: ${enabled} heatingSetpoint: ${heatingSetpoint}")

  if (enabled != null) {
    sendEvent(name: 'switch', value: enabled ? 'on' : 'off', displayed: true, isStateChange: true)
    sendEvent(name: 'thermostatMode', value: enabled ? 'heat' : 'off', displayed: true, isStateChange: true)
  }

  if (heatingSetpoint != null) {
    sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, unit: 'F', displayed: true)
    sendEvent(name: 'thermostatSetpoint', value: heatingSetpoint, unit: 'F', displayed: true)
  }
}

def setThermostatMode(thermostatMode) {
  parent.logDebug("Executing Omnilogic Heater setThermostatMode ${thermostatMode}")
  enableHeater(thermostatMode == 'heat')
}

def heat() {
  parent.logDebug('Executing Omnilogic Heater heat')
  enableHeater(true)
}

def off() {
	parent.logDebug('Executing Omnilogic Heater off')
  enableHeater(false)
}

def enableHeater(enable) {
  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'HeaterID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'Version', dataType: 'string', value: 0],
    [name: 'Enabled', dataType: 'bool', value: enable]
  ]

  parent.performApiRequest('SetHeaterEnable', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      updateState(enable, null)
    }
  }
}

def setHeatingSetpoint(temperature) {
  parent.logDebug("Executing Omnilogic Heater setHeatingSetpoint ${temperature}")

  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('bowId')],
    [name: 'HeaterID', dataType: 'int', value: device.currentValue('omnilogicId')],
    [name: 'Version', value: 0],
    [name: 'Temp', dataType: 'int', value: temperature]
  ]

  parent.performApiRequest('SetUIHeaterCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      updateState(null, temperature)
    }
  }
}
