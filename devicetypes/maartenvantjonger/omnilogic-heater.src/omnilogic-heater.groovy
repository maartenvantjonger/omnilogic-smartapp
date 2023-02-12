/**
 *  OmniLogic Heater
 *
 *  Version: 1.0.0
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition (
    name: "OmniLogic Heater",
    namespace: "maartenvantjonger",
    author: "Maarten van Tjonger",
    ocfDeviceType: "oic.d.thermostat"
  ) {
    capability "Sensor"
    capability "Actuator"
    capability "Refresh"
    capability "Thermostat"
    capability "Temperature Measurement"

    attribute "bowId", "number"
    attribute "omnilogicId", "number"
    attribute "omnilogicHeaterId", "number"
    attribute "lastTemperature", "number"
    attribute "lastTemperatureDate", "string"
    attribute "temperatureUnit", "string"
    attribute "coolingSetpointRange", "vector3"
    attribute "heatingSetpointRange", "vector3"
  }

  preferences {
    input(
      name: "useLastTemperature",
      type: "bool",
      title: "Show last recorded actual temperature.",
      description: "Ignore invalid temperature updates when the filter pump is off.",
      defaultValue: true
    )
  }
}

def initialize(omnilogicId, attributes) {
  logMethod("initialize", "Arguments", [omnilogicId, attributes])

  // Using .contains() because temperatureUnit can contain the unit of the secondary sensor type like UNITS_FAHRENHEITUNITS_ACTIVE_INACTIVE
  def deviceTemperatureUnit = attributes.temperatureUnit.contains("UNITS_FAHRENHEIT") ? "F" : "C"
  def hubTemperatureUnit = getTemperatureScale()
  def setpointRange = [
    convertTemperatureToHubUnit(attributes.minTemperature, deviceTemperatureUnit),
    convertTemperatureToHubUnit(attributes.maxTemperature, deviceTemperatureUnit)
  ]

  sendEvent(name: "omnilogicId", value: omnilogicId, displayed: true)
  sendEvent(name: "omnilogicHeaterId", value: attributes.omnilogicHeaterId, displayed: true)
  sendEvent(name: "bowId", value: attributes["bowId"], displayed: true)
  sendEvent(name: "supportedThermostatModes", value: ["off", "heat"], displayed: true)
  sendEvent(name: "thermostatMode", value: "off", displayed: true)
  sendEvent(name: "thermostatOperatingState", value: "idle", displayed: true)
  sendEvent(name: "supportedThermostatFanModes", value: ["auto"], displayed: true)
  sendEvent(name: "thermostatFanMode", value: "auto", displayed: true)
  sendEvent(name: "switch", value: "off", displayed: true)
  sendEvent(name: "temperature", value: 0, unit: temperatureUnit, displayed: true)
  sendEvent(name: "coolingSetpoint", value: 0, unit: temperatureUnit, displayed: true)
  sendEvent(name: "coolingSetpointRange", value: setpointRange, unit: hubTemperatureUnit, displayed: true)
  sendEvent(name: "heatingSetpoint", value: 0, unit: temperatureUnit, displayed: true)
  sendEvent(name: "heatingSetpointRange", value: setpointRange, unit: hubTemperatureUnit, displayed: true)
  sendEvent(name: "thermostatSetpoint", value: 0, unit: temperatureUnit, displayed: true)
  sendEvent(name: "thermostatSetpointRange", value: setpointRange, unit: hubTemperatureUnit, displayed: true)
  sendEvent(name: "temperatureUnit", value: deviceTemperatureUnit, displayed: true)
}

def refresh() {
  logMethod("refresh")
  parent.updateDeviceStatuses()
}

def parseStatus(deviceStatus, telemetryData) {
  logMethod("parseStatus", "Arguments", [deviceStatus])

  def enabled = deviceStatus.@enable.text() == "yes"
  sendEvent(name: "switch", value: enabled ? "on" : "off", displayed: true)
  sendEvent(name: "thermostatMode", value: enabled ? "heat" : "off", displayed: true)

  def heatingSetpoint = deviceStatus["@Current-Set-Point"].text().toInteger()
  def heatingSetpointInHubUnit = convertTemperatureToHubUnit(heatingSetpoint)
  def hubTemperatureUnit = getTemperatureScale()

  sendEvent(name: "heatingSetpoint", value: heatingSetpointInHubUnit, unit: hubTemperatureUnit, displayed: true)
  sendEvent(name: "thermostatSetpoint", value: heatingSetpointInHubUnit, unit: hubTemperatureUnit, displayed: true)

  // Get current temperature from parent body of water
  def bowStatus = telemetryData.children().find { it.@systemId == device.currentValue("bowId") }
  def temperature = bowStatus?.@waterTemp.text()

  if (temperature != null && temperature != "-1") {
  def temperatureInHubUnit = convertTemperatureToHubUnit(temperature)
    sendEvent(name: "temperature", value: temperatureInHubUnit, unit: hubTemperatureUnit, displayed: true)
    sendEvent(name: "lastTemperature", value: temperatureInHubUnit, unit: hubTemperatureUnit, displayed: true)
    sendEvent(name: "lastTemperatureDate", value: new Date().format("yyyy-MM-dd'T'HH:mm:ss"), displayed: true)
  } else if (settings.useLastTemperature == false) {
    sendEvent(name: "temperature", value: null, unit: hubTemperatureUnit, displayed: true)
  }

  // Get current heater operating state
  def heaterStatus = telemetryData.children().find { it.@systemId == device.currentValue("omnilogicHeaterId") }
  def thermostatOperatingState = heaterStatus.@heaterState.text() == "1" ? "heating" : "idle"
  sendEvent(name: "thermostatOperatingState", value: thermostatOperatingState, displayed: true)
}

def setThermostatMode(thermostatMode) {
  logMethod("setThermostatMode", "Arguments", [thermostatMode])
  enableHeater(thermostatMode == "heat")
}

def heat() {
  logMethod("heat")
  enableHeater(true)
}

def on() {
  logMethod("on")
  enableHeater(true)
}

def off() {
  logMethod("off")
  enableHeater(false)
}

def fanAuto() {
  logMethod("fanAuto")
  setThermostatFanMode("auto")
}

def setThermostatFanMode(fanMode) {
  sendEvent(name: "thermostatFanMode", value: fanMode, displayed: true)
}

def enableHeater(enable) {
  logMethod("enableHeater", "Arguments", [enable])

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "HeaterID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "Enabled", dataType: "bool", value: enable]
  ]

  parent.performApiRequest("SetHeaterEnable", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      sendEvent(name: "switch", value: enable ? "on" : "off", displayed: true, isStateChange: true)
      sendEvent(name: "thermostatMode", value: enable ? "heat" : "off", displayed: true, isStateChange: true)
      updateDataValue("lastRunningMode", enable ? "heat" : "off")
    }
  }
}

def setHeatingSetpoint(temperature) {
  logMethod("setHeatingSetpoint", "Arguments", [temperature])

  def temperatureInDeviceUnit = convertTemperatureToDeviceUnit(temperature).toInteger()

  def parameters = [
    [name: "PoolID", dataType: "int", value: device.currentValue("bowId")],
    [name: "HeaterID", dataType: "int", value: device.currentValue("omnilogicId")],
    [name: "Temp", dataType: "int", value: temperatureInDeviceUnit]
  ]

  parent.performApiRequest("SetUIHeaterCmd", parameters) { response ->
    def success = response.Parameters.Parameter.find { it.@name == "Status" }.text() == "0"
    if (success) {
      def hubTemperatureUnit = getTemperatureScale()
      def temperatureInHubUnit = convertTemperatureToHubUnit(temperature)
      sendEvent(name: "heatingSetpoint", value: temperatureInHubUnit, unit: hubTemperatureUnit, displayed: true, isStateChange: true)
      sendEvent(name: "thermostatSetpoint", value: temperatureInHubUnit, unit: hubTemperatureUnit, displayed: true, isStateChange: true)
    }
  }
}

def convertTemperatureToHubUnit(temperature, deviceTemperatureUnit = null) {
  def temperatureUnit = deviceTemperatureUnit ?: device.currentValue("temperatureUnit")
  return convertTemperatureIfNeeded(temperature?.toBigDecimal(), temperatureUnit, 0)
}

def convertTemperatureToDeviceUnit(temperature) {
  def deviceTemperatureUnit = device.currentValue("temperatureUnit")
  def hubTemperatureUnit = getTemperatureScale()

  if (hubTemperatureUnit == "C" && deviceTemperatureUnit == "F") {
    return celsiusToFahrenheit(temperature)
  }

  if (hubTemperatureUnit == "F" && deviceTemperatureUnit == "C") {
    return fahrenheitToCelsius(temperature)
  }

  return temperature
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}

