/**
 *  Omnilogic Temperature Sensor
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition(
    name: "Omnilogic Temperature Sensor",
    namespace: "maartenvantjonger",
    author: "Maarten van Tjonger"
  ) {
    capability "Sensor"
    capability "Refresh"
    capability "Temperature Measurement"

    attribute "bowId", "number"
    attribute "omnilogicId", "number"
    attribute "lastTemperature", "number"
    attribute "lastTemperatureDate", "string"
    attribute "sensorType", "string"
    attribute "temperatureUnit", "string"
  }

  tiles {
    valueTile("lastTemperature", "device.lastTemperature", width: 2, height: 2, canChangeIcon: true) {
      state("lastTemperature", label: "${currentValue}")
    }

    valueTile("temperature", "device.temperature", width: 2, height: 2, canChangeIcon: true) {
      state("temperature", label: "${currentValue}")
    }

    main("lastTemperature")
    details(["lastTemperature", "temperature"])
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

  def sensorType = attributes["sensorType"] == "SENSOR_WATER_TEMP" ? "water" : "air"
  def deviceTemperatureUnit = attributes["temperatureUnit"] == "UNITS_FAHRENHEIT" ? "F" : "C"
  def hubTemperatureUnit = getTemperatureScale()

  sendEvent(name: "omnilogicId", value: omnilogicId, displayed: true)
  sendEvent(name: "bowId", value: attributes["bowId"], displayed: true)
  sendEvent(name: "sensorType", value: sensorType, displayed: true)
  sendEvent(name: "temperature", value: 0, unit: hubTemperatureUnit, displayed: true)
  sendEvent(name: "temperatureUnit", value: deviceTemperatureUnit, displayed: true)
}

def refresh() {
	logMethod("refresh")
  parent.updateDeviceStatuses()
}

def parseStatus(deviceStatus, telemetryData) {
	logMethod("parseStatus", "Arguments", [deviceStatus])

  def temperature = device.currentValue("sensorType") == "water" ?
    deviceStatus?.@waterTemp?.text() : deviceStatus?.@airTemp?.text()
  def hubTemperatureUnit = getTemperatureScale()

  if (temperature != null && temperature != "-1") {
    def temperatureInHubUnit = convertTemperatureToHubUnit(temperature)
    sendEvent(name: "temperature", value: temperatureInHubUnit, unit: hubTemperatureUnit, displayed: true, isStatusChange: true)
    sendEvent(name: "lastTemperature", value: temperatureInHubUnit, unit: hubTemperatureUnit, displayed: true)
    sendEvent(name: "lastTemperatureDate", value: new Date().format("yyyy-MM-dd'T'HH:mm:ss"), displayed: true)
  } else if (settings.useLastTemperature == false) {
    sendEvent(name: "temperature", value: null, unit: hubTemperatureUnit, displayed: true, isStatusChange: true)
  }
}

def convertTemperatureToHubUnit(temperature) {
  def deviceTemperatureUnit = device.currentValue("temperatureUnit")
  return convertTemperatureIfNeeded(temperature?.toBigDecimal(), deviceTemperatureUnit, 0)
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
