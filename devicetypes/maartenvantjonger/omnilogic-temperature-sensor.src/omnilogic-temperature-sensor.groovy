/**
 *  Omnilogic Temperature Sensor
 *
 *  Copyright 2021 Maarten van Tjonger
 */
metadata {
  definition(
    name: 'Omnilogic Temperature Sensor',
    namespace: 'maartenvantjonger',
    author: 'Maarten van Tjonger'
  ) {
    capability 'Sensor'
    capability 'Refresh'
    capability 'Health Check'
    capability 'Polling'
    capability 'Temperature Measurement'
    attribute 'omnilogicId', 'string'
    attribute 'lastTemperature', 'number'
    attribute 'lastTemperatureDate', 'string'
  }

  tiles {
    valueTile('lastTemperature', 'device.lastTemperature', width: 2, height: 2, canChangeIcon: true) {
      state('lastTemperature', label: '${currentValue}')
    }

    valueTile('temperature', 'device.temperature', width: 2, height: 2, canChangeIcon: true) {
      state('temperature', label: '${currentValue}')
    }

    main('lastTemperature')
    details(['lastTemperature', 'temperature'])
  }

  preferences {
    input(name: 'useLastTemperature', type: 'bool', title: 'Show last recorded actual temperature. Ignore -1 temperature updates.', defaultValue: true)
  }
}

def initialize(omnilogicId, attributes) {
  logMethod('initialize', 'Arguments', [omnilogicId, attributes])

  def sensorType = attributes['sensorType'] == 'SENSOR_WATER_TEMP' ? 'water' : 'air'
  def temperatureUnit = attributes['temperatureUnit'] == 'UNITS_FAHRENHEIT' ? 'F' : 'C'

  sendEvent(name: 'omnilogicId', value: omnilogicId, displayed: true)
  sendEvent(name: 'bowId', value: attributes['bowId'], displayed: true)
  sendEvent(name: 'sensorType', value: sensorType, displayed: true)
  sendEvent(name: 'temperature', value: 0, unit: temperatureUnit, displayed: true)
  sendEvent(name: 'unit', value: temperatureUnit, displayed: true)
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

  def temperature = device.currentValue('sensorType') == 'water' ?
    deviceStatus?.@waterTemp?.text() : deviceStatus?.@airTemp?.text()

  if (temperature != null && temperature != '-1') {
    sendEvent(name: 'temperature', value: temperature, unit: device.currentValue('unit'), displayed: true, isStatusChange: true)
    sendEvent(name: 'lastTemperature', value: temperature, unit: device.currentValue('unit'), displayed: true)
    sendEvent(name: 'lastTemperatureDate', value: new Date().format("yyyy-MM-dd'T'HH:mm:ss"), displayed: true)
  } else if (settings.useLastTemperature == false) {
    sendEvent(name: 'temperature', value: temperature, unit: device.currentValue('unit'), displayed: true, isStatusChange: true)
  }
}

def logMethod(method, message = null, arguments = null) {
  parent.logMethod(device, method, message, arguments)
}
