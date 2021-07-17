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
    capability 'Sensor'
    capability 'Actuator'
    capability 'Refresh'
    capability 'Health Check'
    capability 'Temperature Measurement'
    capability 'Thermostat Heating Setpoint'
    capability 'Thermostat Mode'
    attribute 'bowId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'lastTemperature', 'number'
    attribute 'lastTemperatureDate', 'string'
  }

  preferences {
    input(name: 'useLastTemperature', type: 'bool', title: 'Show last recorded actual temperature. Ignore -1 temperature updates.', defaultValue: true)
  }
}

def initialize(omnilogicId, attributes) {
	parent.logDebug('Executing Omnilogic Heater initialize')

  def temperatureUnit = attributes['temperatureUnit'] == 'UNITS_FAHRENHEIT' ? 'F' : 'C'

  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
  sendEvent(name: 'supportedThermostatModes', value: ['off', 'heat'], displayed: true)
  sendEvent(name: 'thermostatMode', value: 'off', displayed: true)
  sendEvent(name: 'switch', value: 'off', displayed: true)
  sendEvent(name: 'temperature', value: 0, unit: temperatureUnit, displayed: true)
  sendEvent(name: 'heatingSetpoint', value: 0, unit: temperatureUnit, displayed: true)
  sendEvent(name: 'unit', value: temperatureUnit, displayed: true)
}

def refresh() {
	parent.logDebug('Executing Omnilogic Heater refresh')
  parent.updateDeviceStatuses()
}

def ping() {
	parent.logDebug('Executing Omnilogic Heater ping')
  refresh()
}

def parseStatus(deviceStatus, telemetryData) {
	parent.logDebug('Executing Omnilogic Heater parseStatus')
	parent.logDebug(deviceStatus)

  def enabled = deviceStatus.@enable.text() == 'yes'
  sendEvent(name: 'switch', value: enabled ? 'on' : 'off', displayed: true)
  sendEvent(name: 'thermostatMode', value: enabled ? 'heat' : 'off', displayed: true)

  def heatingSetpoint = deviceStatus['@Current-Set-Point'].text().toInteger()
  sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, unit: device.currentValue('unit'), displayed: true)

  // Get current temperature from parent body of water
  def bowStatus = telemetryData.children().find { it.@systemId == device.currentValue('bowId') }
  def temperature = bowStatus?.@waterTemp.text()

  if (temperature != null && temperature != '-1') {
    sendEvent(name: 'temperature', value: temperature, unit: device.currentValue('unit'), displayed: true, isStatusChange: true)
    sendEvent(name: 'lastTemperature', value: temperature, unit: device.currentValue('unit'), displayed: true)
    sendEvent(name: 'lastTemperatureDate', value: new Date().format("yyyy-MM-dd'T'HH:mm:ss"), displayed: true)
  } else if (settings.useLastTemperature == false) {
    sendEvent(name: 'temperature', value: temperature, unit: device.currentValue('unit'), displayed: true, isStatusChange: true)
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
      sendEvent(name: 'switch', value: enable ? 'on' : 'off', displayed: true, isStateChange: true)
      sendEvent(name: 'thermostatMode', value: enable ? 'heat' : 'off', displayed: true, isStateChange: true)
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
      sendEvent(name: 'heatingSetpoint', value: temperature, unit: device.currentValue('unit'), displayed: true, isStateChange: true)
    }
  }
}
