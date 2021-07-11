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
    capability "Thermostat Cooling Setpoint"
    capability "Thermostat Heating Setpoint"
    capability "Thermostat Operating State"
    capability "Thermostat Mode"
    attribute 'poolId', 'number'
    attribute 'omnilogicId', 'number'
    attribute 'coolingSetpoint', 'number'
    attribute 'heatingSetpoint', 'number'
    attribute 'hysteresis', 'number'
    attribute 'supportedThermostatFanModes', "enum", ["low", "high"]
    attribute 'supportedThermostatModes', "enum", ["off", "heat"]
    attribute 'temperature', 'number'
    attribute 'thermostatFanMode', 'string'
    attribute 'thermostatMode', 'string'
    attribute 'thermostatOperatingState', 'string'
    attribute 'thermostatSetpoint', 'number'
  }

  tiles {
    controlTile('heatingSetpoint', 'device.heatingSetpoint', 'slider', range: '(65..104)', height: 2, width: 2, canChangeIcon: true, decoration: 'flat', inactiveLabel: false) {
      state 'heatingSetpoint', action: 'setHeatingSetpoint'
    }

    multiAttributeTile(name:"thermostatFull", type:"thermostat", width:6, height:4) {
      tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
          attributeState("temp", label:'${currentValue}', unit:"dF", defaultState: true)
      }
      tileAttribute("device.temperature", key: "VALUE_CONTROL") {
          attributeState("VALUE_UP", action: "tempUp")
          attributeState("VALUE_DOWN", action: "tempDown")
      }
      tileAttribute("device.thermostatOperatingState", key: "OPERATING_STATE") {
          attributeState("idle", backgroundColor:"#00A0DC")
          attributeState("heating", backgroundColor:"#e86d13")
      }
      tileAttribute("device.thermostatMode", key: "THERMOSTAT_MODE") {
          attributeState("off", label:'${name}')
          attributeState("heat", label:'${name}')
      }
      tileAttribute("device.heatingSetpoint", key: "HEATING_SETPOINT") {
          attributeState("heatingSetpoint", label:'${currentValue}', unit:"dF", defaultState: true)
      }
    }

    main('heatingSetpoint')
		details(['heatingSetpoint', 'thermostatFull'])
  }
}

def parse(statusXmlNode) {
	parent.logDebug('Executing Omnilogic Heater parse')
	parent.logDebug(statusXmlNode)

  def enabled = statusXmlNode.@enable.text() == 'yes'
  def heatingSetpoint = statusXmlNode['@Current-Set-Point'].text().toInteger()
  updateState(enabled, heatingSetpoint)
}


def updateState(enabled, heatingSetpoint) {
  parent.logDebug("Executing Omnilogic Heater updateState enabled: ${enabled} heatingSetpoint: ${heatingSetpoint}")

  sendEvent(name: 'switch', value: enabled ? 'on' : 'off', displayed: true, isStateChange: true)
  sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, displayed: true)
  sendEvent(name: 'thermostatSetpoint', value: heatingSetpoint, displayed: true)
  sendEvent(name: 'thermostatMode', value: enabled ? 'heat' : 'off', displayed: true, isStateChange: true)
  sendEvent(name: 'thermostatOperatingState', value: enabled ? 'heating' : 'idle', displayed: true, isStateChange: true)
}

def setThermostatMode(thermostatmode) {
  parent.logDebug('Executing Omnilogic Heater setThermostatMode')

}

def setHeatingSetpoint(temperature) {
  parent.logDebug('Executing Omnilogic Heater setHeatingSetpoint')

  def parameters = [
    [name: 'PoolID', dataType: 'int', value: device.currentValue('poolId') ?: 1],
    [name: 'HeaterID', dataType: 'int', value: device.omnilogicId],
    [name: 'Version', value: 0],
    [name: 'Temp', dataType: 'int', value: temperature]
  ]

  parent.performApiRequest('SetUIHeaterCmd', parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == 'Status' }.text() == '0'
    if (success) {
      sendEvent(name: 'heatingSetpoint', value: temperature, displayed: true)
    }
  }
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
